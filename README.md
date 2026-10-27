# MS-AV-02 — Segurança e Observabilidade

Projeto em **Java 21 + Spring Boot 3.5 + Maven**, baseado no enunciado **MS-AV-02 — Segurança e Observabilidade**.

Implementa autenticação REST, autorização via `JdbcUserDetailsManager`, bloqueio após duas falhas consecutivas, troca de senha, métricas com Spring Actuator/Micrometer/Prometheus, Swagger/OpenAPI e dashboard Grafana provisionado automaticamente.

O projeto inclui testes automatizados com JUnit/MockMvc, um ambiente completo com Docker Compose e evidências reais de observabilidade.

## 1. Requisitos

- **Opção recomendada:** Docker Desktop com Docker Compose (executa aplicação Java, Prometheus e Grafana).
- **Opção manual:** JDK 21 e Apache Maven 3.9+ para executar a aplicação e os testes localmente.

## 2. Executar o sistema completo no Windows

No **PowerShell**, dentro da pasta do repositório:

```powershell
Copy-Item .env.example .env
notepad .env
```

Substitua as três senhas de exemplo por senhas próprias. **Não envie o arquivo `.env` ao GitHub:** ele está no `.gitignore`.

As variáveis `APP_ADMIN_PASSWORD` e `APP_MANUTENCAO_PASSWORD` são utilizadas para criar os usuários iniciais apenas se eles ainda não existirem no banco. Alterar o `.env` depois da criação não altera automaticamente as senhas existentes.

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

Quando a aplicação estiver pronta:

| Componente | Endereço |
| --- | --- |
| API REST | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Métricas Prometheus | http://localhost:8080/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

No Grafana, entre com as credenciais `GRAFANA_ADMIN_USER` e `GRAFANA_ADMIN_PASSWORD` do `.env`. Em **Dashboards > MS-AV-02**, abra **MS-AV-02 | Seguranca e Observabilidade**. A fonte de dados Prometheus e o dashboard são provisionados pelos arquivos em `observability/`.

Para encerrar:

```powershell
docker compose down
```

Esse comando preserva os volumes. **Atenção:** `docker compose down -v` remove também os volumes persistentes do H2, Prometheus e Grafana.

## 3. Endpoints da atividade

Os caminhos seguem o enunciado, inclusive a rota raiz `/`. O parâmetro `{userid}` representa `username`, chave primária da tabela de usuários.

| Método | Rota | Acesso | Corpo | Sucesso |
| --- | --- | --- | --- | --- |
| `POST` | `/login` | Público | `{"username":"...","senha":"..."}` | `200` |
| `PUT` | `/trocarsenha/{userid}` | Público, com validação da senha atual | `{"atual":"...","nova":"..."}` | `200` |
| `POST` | `/` | `ROLE_MANUTENCAO` | `{"username":"...","senha":"..."}` | `201` |
| `PUT` | `/unlock/{userid}` | `ROLE_ADMIN` | Sem corpo | `200` |
| `GET` | `/` | `ROLE_MANUTENCAO` | Sem corpo | `200` |

Códigos HTTP utilizados: `400` para dados inválidos, `401` para credenciais inválidas ou falta de autenticação, `403` para falta de permissão, `404` para recurso inexistente e `409` para usuário duplicado.

### Usuários iniciais

| Usuário | Perfil | Finalidade |
| --- | --- | --- |
| `admin` | `ROLE_ADMIN` | Desbloquear usuários |
| `manutencao` | `ROLE_MANUTENCAO` | Cadastrar e listar usuários |
| Usuários cadastrados | `ROLE_USUARIO` | Login e troca de senha |

O endpoint `POST /login` valida credenciais e retorna uma resposta REST, mas **não emite token**. As rotas protegidas utilizam **HTTP Basic**, integrado ao `JdbcUserDetailsManager`, para atender ao controle de permissões da atividade. Essa configuração destina-se ao ambiente de laboratório; em produção, seriam necessários HTTPS e controles adicionais de segurança.

## 4. Teste manual rápido

No **PowerShell**, com os contêineres ativos, use a senha definida em `APP_MANUTENCAO_PASSWORD`:

```powershell
$base = 'http://localhost:8080'
$senhaManutencao = 'COLOQUE_A_SENHA_DO_SEU_ENV'
$cred = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("manutencao:$senhaManutencao"))
$headers = @{ Authorization = "Basic $cred" }

# Cadastrar usuário: HTTP 201
Invoke-RestMethod -Uri "$base/" -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Body (@{ username='teste1'; senha='SenhaSegura#2026' } | ConvertTo-Json)

# Listar usuários, sem expor senhas: HTTP 200
Invoke-RestMethod -Uri "$base/" -Method Get -Headers $headers

# Login válido: HTTP 200
Invoke-RestMethod -Uri "$base/login" -Method Post `
  -ContentType 'application/json' `
  -Body (@{ username='teste1'; senha='SenhaSegura#2026' } | ConvertTo-Json)
```

Para testar **duas falhas consecutivas, bloqueio e desbloqueio**, consulte [`docs/TESTES-MANUAIS.md`](docs/TESTES-MANUAIS.md).

Os contadores de login são atualizados pelo endpoint `/login`. Uma autenticação HTTP Basic em rota protegida não incrementa esses contadores.

## 5. Observabilidade: métricas e dashboard

| Métrica Prometheus | Tipo | Descrição |
| --- | --- | --- |
| `app_logins_valid_total` | Counter | Logins aceitos desde o início do processo |
| `app_logins_invalid_total` | Counter | Tentativas de login recusadas desde o início do processo |
| `app_users_blocked` | Gauge | Quantidade atual de usuários bloqueados no banco |

Os contadores são métricas do processo e reiniciam quando a aplicação reinicia. O estado de bloqueio e a contagem de falhas são persistidos no H2, na tabela `login_attempts`. O Prometheus mantém suas séries temporais em volume próprio.

O dashboard Grafana possui três indicadores numéricos e um gráfico de tendência de logins por minuto.

### Arquivos do dashboard e evidências

| Arquivo | Finalidade |
| --- | --- |
| [`observability/grafana/dashboards/dashboard-ms-av-02.json`](observability/grafana/dashboards/dashboard-ms-av-02.json) | Dashboard JSON utilizado no provisionamento automático |
| [`docs/grafana-dashboard.png`](docs/grafana-dashboard.png) | Captura de tela real do dashboard em execução |
| [`docs/dashboard-exportado.json`](docs/dashboard-exportado.json) | Exportação do dashboard obtida pela interface do Grafana |

A fonte Prometheus é configurada em `observability/grafana/provisioning/datasources/prometheus.yml`, e o dashboard é carregado automaticamente pela configuração de provisionamento.

Para reproduzir as evidências, abra `http://localhost:3000`, gere tentativas válidas e inválidas no endpoint `/login`, aguarde a coleta do Prometheus (a cada 5 segundos) e confira os indicadores. Para exportar o JSON, utilize a opção **Export** do dashboard no Grafana, selecione **Classic** quando disponível e salve o arquivo em `docs/dashboard-exportado.json`.

> A imagem e a exportação JSON são evidências da execução. O JSON em `observability/` continua necessário para provisionar o dashboard automaticamente.

## 6. Executar a aplicação com Maven

Com JDK 21 e Maven instalados, no **PowerShell**:

```powershell
$env:APP_ADMIN_PASSWORD = 'SuaSenhaAdmin#2026'
$env:APP_MANUTENCAO_PASSWORD = 'SuaSenhaManutencao#2026'
mvn clean test
mvn spring-boot:run
```

A aplicação utiliza H2 persistente em `./data/seguranca-db.mv.db`, ignorado pelo Git. O esquema é inicializado por `src/main/resources/schema.sql`.

Ao executar somente o Spring Boot no host, o alvo de coleta `app:8080` configurado no Prometheus do Compose não aponta para a aplicação local; para visualizar o dashboard, utilize o Compose completo ou adapte o alvo de coleta.

## 7. Testes automatizados

```powershell
mvn clean test
mvn clean package
```

A classe `MsAv02IntegrationTest.java` contém **12 testes de integração** com JUnit e MockMvc, cobrindo:

- Login válido e inválido;
- Bloqueio na segunda falha e desbloqueio exclusivo do administrador;
- Rejeição de autenticação de usuário bloqueado;
- Permissões de cadastro e listagem por perfil;
- Cadastro de usuário, tratamento de duplicidade e proteção da senha com BCrypt;
- Troca de senha com validação da senha atual;
- Reinicialização da contagem de falhas após login válido;
- Tratamento de entradas inválidas e usuários inexistentes;
- Disponibilidade do Swagger/OpenAPI, health e métricas Prometheus.

Os testes de integração utilizam `@AutoConfigureObservability` para habilitar o registro de métricas no contexto de testes do Spring Boot 3.5. A troca de senha persiste o hash atualizado por `JdbcUserDetailsManager.updateUser(UserDetails)`.

O workflow [`.github/workflows/testes.yml`](.github/workflows/testes.yml) executa `mvn test` em push e pull request. Consulte a aba **Actions** no GitHub para conferir o resultado de cada execução.

## 8. Estrutura do projeto

```text
.github/workflows/
  testes.yml
src/main/java/br/com/msav02/
  config/          SecurityConfig, OpenApiConfig, BootstrapConfig
  controller/      UsuarioController
  dto/             Requisições e respostas REST
  exception/       Tratamento de erros HTTP
  repository/      Consultas JDBC e controle de bloqueio
  service/         LoginService, UsuarioService
src/main/resources/
  schema.sql
  application.yml
src/test/
  .../MsAv02IntegrationTest.java
observability/
  prometheus/prometheus.yml
  grafana/provisioning/...
  grafana/dashboards/dashboard-ms-av-02.json
docs/
  grafana-dashboard.png
  dashboard-exportado.json
  TESTES-MANUAIS.md
  LEIA-ME-ANTES-DE-ENTREGAR.txt
compose.yml
Dockerfile
.env.example
.gitignore
pom.xml
README.md
```

## 9. Decisões técnicas

- O SQL do enunciado tinha uma vírgula extra antes do fechamento da tabela `login_attempts`; ela foi removida.
- O campo `usuarios.senha` foi ampliado para comportar hashes BCrypt, que têm 60 caracteres.
- O campo `usuarios.enabled` e a tabela `authorities` atendem aos requisitos do `JdbcUserDetailsManager`.
- A tabela `usuarios` é a fonte única de credenciais para o endpoint `/login` e para a autenticação HTTP Basic.
- Duas falhas consecutivas em `/login` bloqueiam o usuário; um login válido antes da segunda falha zera a contagem.
- O desbloqueio exige `ROLE_ADMIN`.
- `{userid}` representa `username`, conforme a chave primária adotada.
- A troca de senha exige a senha atual correta e armazena apenas o novo hash BCrypt.
- O ambiente Docker publica as portas na interface local (`127.0.0.1`) para limitar a exposição durante os testes.

## 10. Checklist de entrega

- [x] Código-fonte publicado no GitHub em repositório público.
- [x] README com requisitos, execução, endpoints e testes.
- [x] Configuração de Docker Compose, Prometheus e Grafana.
- [x] Dashboard JSON de provisionamento incluído no repositório.
- [x] Captura real do dashboard em `docs/grafana-dashboard.png`.
- [x] Testes automatizados executados localmente com sucesso (12 testes).
- [x] Arquivos `.env`, banco H2 e `target/` ignorados pelo Git.
- [ ] Confirmar o envio de `docs/dashboard-exportado.json` ao GitHub.
- [ ] Confirmar o resultado da execução mais recente em **Actions**.

**Repositório:** https://github.com/snowdutra/MS-Seguranca

**Referências:** [Enunciado oficial](https://github.com/esensato/ms-2026-02/blob/main/MS-AV-02-Seguran%C3%A7a-Observabilidade.md), [Spring Security JDBC](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/jdbc.html) e [Provisionamento do Grafana](https://grafana.com/docs/grafana/latest/administration/provisioning/).
