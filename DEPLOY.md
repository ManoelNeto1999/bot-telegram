# Deploy do telegram-saldo

## Arquitetura

```text
Telegram -> Cloudflare Tunnel -> app:8080 -> SQL Server:1433
                                  |
                                  +-> api.telegram.org
```

O Compose deste repositório gerencia somente a aplicação. O SQL Server existente
`telegram-saldo-sqlserver` continua com seu volume e sua configuração atuais. Os
dois containers se comunicam pela rede externa `telegram-saldo-db`. A rede
`telegram-saldo-edge` permite a saída da aplicação para o Telegram e receberá o
`cloudflared` futuramente.

O Compose de produção não publica a porta 8080. O arquivo `compose.local.yaml`
faz bind apenas em `127.0.0.1` para validações por SSH tunnel.

## Requisitos

- Java não é necessário no servidor; o build ocorre em Docker.
- Docker Engine e Docker Compose.
- SQL Server 2022 em execução, com o banco `dbbotwpp` restaurado.
- Rede Docker compartilhada com alias `sqlserver` para o container do banco.
- Um hostname HTTPS futuro para o webhook.

## Build Maven local

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Linux:

```bash
./mvnw test
./mvnw package
```

O profile de teste usa H2 em memória somente para validar o contexto e o
mapeamento das entidades sem infraestrutura externa. As migrations usam sintaxe
específica do SQL Server e são validadas pelo Flyway no SQL Server real, não pelo
H2. Produção e desenvolvimento continuam usando SQL Server.

## Variáveis

Copie o exemplo e preencha somente no ambiente de destino:

```bash
cp .env.example .env
chmod 600 .env
```

Obrigatórias:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_WEBHOOK_PUBLIC_URL`
- `TELEGRAM_WEBHOOK_SECRET`

Opcionais para o POC de Telegram Business:

- `TELEGRAM_BUSINESS_ENABLED` (default `false`);
- `TELEGRAM_BUSINESS_ALLOWED_OWNER_USER_ID` (obrigatória para executar comandos
  quando o modo Business estiver habilitado).

Não habilite `TELEGRAM_BUSINESS_ENABLED` por padrão em produção. O fluxo usa
fail-closed: flag desligada, allowlist ausente, owner divergente, conexão
desconhecida/desabilitada ou sem `rights.can_reply` impedem qualquer chamada ao
serviço financeiro e qualquer resposta Telegram.

Exemplo do formato da URL JDBC, sem credenciais:

```text
jdbc:sqlserver://sqlserver:1433;databaseName=dbbotwpp;encrypt=true;trustServerCertificate=true
```

A URL pública precisa terminar no endpoint real:

```text
https://<HOSTNAME>/api/telegram/webhook
```

O segredo do webhook deve ter de 1 a 256 caracteres e usar apenas letras,
números, `_` e `-`, conforme a API do Telegram.

Use inicialmente `TELEGRAM_WEBHOOK_AUTO_REGISTER=false`. Ative somente depois
que o Cloudflare Tunnel estiver funcional, para não retirar o webhook do ambiente
antigo antes da validação.

`TELEGRAM_WEBHOOK_MANUAL_MANAGEMENT_ENABLED` deve permanecer `false` em
produção. Se for necessário habilitá-lo temporariamente, acesse a aplicação
somente pelo bind local/SSH e desabilite-o logo depois.

## Preparar a rede do banco no srv-home

Primeiro, inspecione sem alterar o container existente:

```bash
docker inspect telegram-saldo-sqlserver --format '{{json .NetworkSettings.Networks}}'
docker network ls
```

Crie a rede somente se ela ainda não existir:

```bash
docker network create --internal telegram-saldo-db
```

Conecte o SQL Server somente se ele ainda não estiver nessa rede:

```bash
docker network connect --alias sqlserver telegram-saldo-db telegram-saldo-sqlserver
```

Confirme que o alias `sqlserver` está presente antes de subir a aplicação. Não
publique a porta 1433 na Internet.

## Instalação no servidor

Estrutura recomendada:

```text
/opt/stacks/telegram-saldo   código, Compose e .env
/srv/data/telegram-saldo     reservado para dados operacionais da stack
```

O app não requer volume persistente. Não mova o volume atual do SQL Server sem
backup e uma etapa separada de migração.

Na pasta `/opt/stacks/telegram-saldo`:

```bash
docker compose build app
docker compose up -d app
docker compose ps
docker compose logs -f app
```

Parar a aplicação:

```bash
docker compose down
```

O restart policy é `unless-stopped`, portanto a aplicação volta após reboot do
servidor, salvo se tiver sido parada explicitamente.

## Validação sem alterar o webhook

Mantenha `TELEGRAM_WEBHOOK_AUTO_REGISTER=false` e suba com o override local:

```bash
docker compose -f compose.yaml -f compose.local.yaml up -d app
```

No próprio servidor:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
docker compose logs app
```

De outra máquina, use um SSH tunnel para `127.0.0.1:8080`. Não faça bind da
porta em `0.0.0.0`.

O healthcheck inclui a saúde do datasource. A aplicação somente ficará saudável
quando conectar ao SQL Server, o Flyway validar V1-V3 e o Hibernate validar o
schema.

Confirme no banco, em modo somente leitura:

```sql
SELECT COUNT(*) FROM flyway_schema_history;
SELECT COUNT(*) FROM movimentacoes_saldo;
SELECT COUNT(*) FROM usuarios_saldo;
```

Os valores esperados antes do teste funcional são 3, 85 e 7, respectivamente.

## Registro automático do webhook

Depois do `ApplicationReadyEvent`, a aplicação:

1. verifica `TELEGRAM_WEBHOOK_AUTO_REGISTER`;
2. consulta `getWebhookInfo`;
3. compara a URL atual com `TELEGRAM_WEBHOOK_PUBLIC_URL` e confere, sem considerar
   ordem, o conjunto exato de `allowed_updates`;
4. mantém o webhook somente se URL e `allowed_updates` estiverem corretos;
5. caso contrário, chama `setWebhook` com URL, `secret_token` e os updates
   `message`, `business_connection`, `business_message`,
   `edited_business_message` e `deleted_business_messages`;
6. registra erro sem encerrar a aplicação se o Telegram estiver indisponível.

Quando a URL já estiver correta, o Telegram não informa qual secret está
registrado. Portanto, uma rotação apenas do secret exige forçar novo `setWebhook`:
altere temporariamente a URL, remova o webhook pela API oficial ou habilite o
endpoint administrativo somente via SSH. Nunca exponha esse endpoint no Tunnel.

## Cloudflare Tunnel futuro

O container `cloudflared` deve entrar apenas na rede `telegram-saldo-edge` e usar:

```text
http://app:8080
```

A regra pública deve aceitar somente o endpoint do Telegram e terminar em uma
regra catch-all 404. Exemplo conceitual:

```yaml
ingress:
  - hostname: <HOSTNAME>
    path: ^/api/telegram/webhook$
    service: http://app:8080
  - service: http_status:404
```

Não publique `/api/bot/*`, `/api/telegram/set-webhook`,
`/api/telegram/webhook-info` nem `/actuator/*`.

Quando o Tunnel estiver validado:

1. defina a URL HTTPS definitiva no `.env`;
2. mantenha o secret preenchido;
3. altere `TELEGRAM_WEBHOOK_AUTO_REGISTER=true`;
4. recrie o app com `docker compose up -d --force-recreate app`;
5. confira os logs e faça um teste real no Telegram;
6. somente então encerre ngrok e o processo no Windows.

## Logs

```bash
docker compose logs -f app
docker compose logs --since 30m app
```

Os logs vão para stdout/stderr. O driver `json-file` mantém até cinco arquivos de
10 MB. Token, senha, secret, texto da mensagem e payload completo não são
registrados pelos logs explícitos da integração. No POC Business, IDs técnicos
de update, conexão, chat, mensagem e owner são registrados para auditoria do
fluxo controlado.

## POC Telegram Connected Business Bot

O modo tradicional continua processando `update.message` e responde sem
`business_connection_id`. Quando a feature flag está habilitada, o modo Business
processa apenas `update.business_message` textual em chat privado, resolve a
conexão pelo cache em memória ou por `getBusinessConnection`, valida conexão
ativa, `rights.can_reply` e o owner explicitamente autorizado, e responde com o
mesmo `business_connection_id`.

Mensagens de saída (`outgoing`), mensagens com `sender_business_bot`, remetentes
bot e mensagens cujo remetente é o próprio owner são ignoradas para evitar loop.
Edições e exclusões são somente registradas; não há reconciliação financeira.

Este POC mantém o `chatId` como identidade financeira apenas porque suporta uma
única conta Business autorizada em ambiente controlado. **ISTO NÃO É SEGURO PARA
MÚLTIPLAS EMPRESAS.** Ainda não existe `tenant_id`, deduplicação persistente nem
outbox, e `business_connection_id` não deve ser tratado como tenant permanente.
Não use o POC com múltiplas empresas reais.

Também permanece necessária uma validação manual no Telegram para confirmar se
o formato Connected Business elimina os sponsored messages observados no chat
direto tradicional com o bot.

## Rollback

Antes de cada deploy, preserve a tag anterior da imagem e faça backup do banco.
Como esta preparação não adiciona migrations, o rollback da aplicação não exige
rollback de schema.

Para voltar a imagem:

1. defina `APP_VERSION` no `.env` com a tag anterior;
2. execute `docker compose up -d --no-build app`;
3. confira `/actuator/health` e os logs.

Durante a troca ngrok/Cloudflare, mantenha o ambiente antigo disponível. Para
rollback de tráfego, configure `TELEGRAM_WEBHOOK_PUBLIC_URL` com a URL antiga,
recrie o app para o auto-registro e só depois pare a nova aplicação.

## Segurança operacional

- Nunca faça commit do `.env`.
- Use `chmod 600 .env`.
- Rotacione o token Telegram já versionado anteriormente.
- Rotacione a senha SQL já versionada anteriormente.
- Prefira um login SQL dedicado; `sa` continua compatível apenas para transição.
- Não reescreva o histórico Git sem planejamento e aprovação.
- Não exponha 1433 ou 8080 publicamente.
