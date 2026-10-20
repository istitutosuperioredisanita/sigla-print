# SIGLA Print

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)

Componente Spring Boot di **SIGLA** (sistema contabile degli enti di ricerca) che produce **stampe** (PDF, tramite JasperReports) ed **estrazioni Excel** a partire da una coda di richieste. Le richieste arrivano da SIGLA attraverso uno spooler su database, vengono accodate per priorità, elaborate in modo asincrono e il risultato viene salvato su uno storage (CMIS, S3 o filesystem) da cui l'utente può scaricarlo.

## Indice

- [Come funziona](#come-funziona)
- [Requisiti](#requisiti)
- [Build](#build)
- [Configurazione](#configurazione)
- [Esecuzione](#esecuzione)
- [API](#api)
- [Docker](#docker)
- [Sorgente dei report (GitHub / GitLab)](#sorgente-dei-report-github--gitlab)
- [Test](#test)
- [Release e CI](#release-e-ci)
- [Note di migrazione](#note-di-migrazione)
- [Licenza](#licenza)

## Come funziona

1. SIGLA inserisce una richiesta di stampa (`PrintSpooler`) o di estrazione (`ExcelSpooler`) nel database.
2. Uno scheduler (`print.scheduler`, in millisecondi) legge le richieste in attesa per ciascuna priorità (`print.queue.priorita`) e le mette in una coda **Hazelcast** (`SIGLA_PRIORITA_<n>`).
3. Un listener sulla coda prende l'evento, acquisisce un **lock distribuito** sulla singola stampa (così più istanze non elaborano lo stesso lavoro) e compila/riempie il report Jasper.
4. Il file prodotto viene scritto sullo **storage** con path `<utente>/<nome file>`; lo stato della richiesta passa a `S` (successo) o `E` (errore) e, se richiesto, viene inviata una e-mail con l'allegato.
5. Il file è scaricabile via REST; un job pianificato (`print.deletecron`) elimina le stampe scadute.

I template `.jrxml`/`.jasper` e le immagini sono letti dal repository dei report (vedi [sotto](#sorgente-dei-report-github--gitlab)) e tenuti in cache (cache `jasper-cache`, con eviction tramite webhook).

## Requisiti

| Componente | Versione |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| Database | Oracle (driver `ojdbc8`) o PostgreSQL; H2 per i test |
| Storage | CMIS (es. Alfresco), Amazon S3 o filesystem |

Il progetto eredita dal parent `it.cnr.si:archetype`; le dipendenze `it.cnr.si.*` e il pubblicazione dei pacchetti usano **GitHub Packages** (`.m2/settings.xml` con `GITHUB_USERNAME` e `GITHUB_TOKEN`).

## Build

```bash
mvn clean package            # genera target/sigla-print-<versione>.war
mvn clean package -DskipTests
```

Il risultato è un WAR eseguibile (`java -jar`).

## Configurazione

La configurazione è in `src/main/resources/application*.yml` ed è organizzata per **profili**: `common` (base), `dev`, `prod`, e il profilo della sorgente report (`github` o `gitlab`). Ogni valore si può sovrascrivere da riga di comando (`--chiave=valore`) o con variabili d'ambiente.

```bash
--spring.profiles.active=common,prod,github
```

### Proprietà principali

| Proprietà | Default | Descrizione |
|---|---|---|
| `spring.datasource.url / username / password` | – | Connessione al database SIGLA |
| `spring.mail.host`, `spring.mail.from` | – | SMTP per l'invio delle stampe via e-mail |
| `print.server.url` | `http://localhost:8080` | URL pubblico del servizio, usato nei link di download |
| `print.scheduler` | `5000` | Intervallo (ms) dello scheduler che legge lo spooler |
| `print.deletecron` | `0 0 2 1/1 * ?` | Cron di pulizia delle stampe scadute |
| `print.queue.priorita` | `1,2,3,4,5` | Priorità gestite (una coda Hazelcast ciascuna) |
| `print.max.page.size` | `1000` | Dimensione massima di pagina per le estrazioni |
| `print.virtualizer.enable` | `true` | Usa il virtualizer file di Jasper per report grandi |
| `print.output.dir` | `${user.home}/print` | Directory di lavoro temporanea |
| `print.storage.base-path` | `/sigla-print` | Cartella radice sullo storage |
| `dir.image` | `/img/` | Percorso delle immagini nei report |

### Storage

Lo storage è fornito da [storage-cloud](https://github.com/consiglionazionaledellericerche/storage-cloud) (`it.cnr.si.storage`, versione 3.0.0). Il driver si sceglie con `cnr.storage.driver`: `cmis`, `s3`, `filesystem` (o `azure`, se si aggiunge il modulo).

**Filesystem** (sviluppo e test):

```properties
cnr.storage.driver=filesystem
cnr.storage.filesystem.directory=/tmp/stampez/
```

**Amazon S3**:

```properties
cnr.storage.driver=s3
cnr.storage.s3.authUrl=
cnr.storage.s3.accessKey=
cnr.storage.s3.secretKey=
cnr.storage.s3.bucketName=
cnr.storage.s3.signingRegion=
cnr.storage.s3.deleteAfter=
```

**CMIS** (es. Alfresco):

```properties
cnr.storage.driver=cmis
repository.base.url=https://cmis.example.it/alfresco
org.apache.chemistry.opencmis.binding.spi.type=browser
org.apache.chemistry.opencmis.binding.browser.url=https://cmis.example.it/alfresco/api/-default-/public/cmis/versions/1.1/browser
org.apache.chemistry.opencmis.session.repository.id=-default-
org.apache.chemistry.opencmis.user=
org.apache.chemistry.opencmis.password=
```

> L'app non parte se `cnr.storage.driver` non è impostato.

### Cluster e cache (Hazelcast)

Code e lock sono su Hazelcast. Con più istanze nella stessa rete, il default (discovery automatica) le unisce in cluster; per ambienti che non supportano il multicast, fornire un `hazelcast.xml`/`hazelcast.yaml` (o `spring.hazelcast.config`) con la configurazione di rete desiderata.

### Log

- `logback-local.xml` – solo console (default e `dev`).
- `logback-prod.xml` – console, file con rotazione (`LOG_FILE`, default `logs/sigla-print.log`, 50 MB × 30 giorni) e **syslog** (`syslog.host`, `syslog.port`, `syslog.url`).

## Esecuzione

```bash
java -Duser.country=IT -Duser.language=it -Dfile.encoding=UTF8 \
     -jar target/sigla-print-*.war \
     --spring.profiles.active=common,dev,github \
     --cnr.storage.driver=filesystem \
     --cnr.storage.filesystem.directory=/tmp/stampez/
```

L'applicazione ascolta su `http://localhost:8080`. Gli endpoint actuator sono esposti alla radice (`management.endpoints.web.base-path: /`), ad esempio `/health`; la documentazione OpenAPI è disponibile con springdoc su `/v3/api-docs` e `/swagger-ui.html`.

## API

| Metodo | Path | Descrizione |
|---|---|---|
| `POST` | `/api/v1/get/print` | Genera subito (in modo sincrono) la stampa descritta dal `PrintSpooler` nel body e restituisce il PDF |
| `POST` | `/api/v1/get/print/dsOnBody` | Accoda una stampa il cui datasource JSON è passato nel body (richiede l'header `ds-utente`) |
| `GET` | `/api/v1/get/print/{user}/{name}` | Scarica un PDF già prodotto |
| `DELETE` | `/api/v1/get/print/{user}/{name}` | Elimina un PDF |
| `GET` | `/api/v1/get/excel/{name}` | Scarica un'estrazione Excel (parametri `user` e `file`, opzionale `command`) |
| `POST` | `/api/v1/printSpooler` | Crea una richiesta di stampa nello spooler e restituisce il progressivo `pg_stampa` |
| `GET` / `PUT` / `DELETE` | `/api/v1/printSpooler/{pg_stampa}` | Legge, aggiorna o elimina una richiesta nello spooler |
| `POST` | `/api/v1/hook` | Webhook del repository dei report: invalida la cache dei template modificati |

Esempi:

```bash
# Salute del servizio
curl -s http://localhost:8080/health

# Stampa immediata (PrintSpooler in JSON) e salvataggio del PDF
curl -s -X POST http://localhost:8080/api/v1/get/print \
  -H 'Content-type: application/json' \
  --data @richiesta-stampa.json -o stampa.pdf

# Simulazione di un webhook di commit
curl -s -X POST http://localhost:8080/api/v1/hook \
  -H 'Content-type: application/json' \
  --data @src/test/resources/lint.json
```

Gli endpoint `/api/v1/printSpooler/*` e `/api/v1/get/print/dsOnBody` ricevono l'utente dall'header HTTP `ds-utente`.

## Docker

Il `Dockerfile` usa `eclipse-temurin:21-jre-alpine` e copia il WAR in `/opt/sigla-print.war`:

```bash
mvn clean package -DskipTests
docker build -t sigla-print .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=common,prod,github \
  -e CNR_STORAGE_DRIVER=filesystem \
  -e CNR_STORAGE_FILESYSTEM_DIRECTORY=/data \
  -v sigla-print-data:/data \
  sigla-print
```

`docker-compose.yml.tpl` è il template usato dalla pipeline di deploy interna (i segnaposto `##{...}##` vengono sostituiti in fase di rilascio).

Le immagini vengono pubblicate su `ghcr.io/istitutosuperioredisanita/sigla-print` (`latest` dal branch di sviluppo, e il tag della release).

## Sorgente dei report (GitHub / GitLab)

I report sono in un repository separato, selezionato dal profilo attivo:

```yaml
# profilo github
cnr.github:
  token:                    # token di accesso
  repo: consiglionazionaledellericerche/sigla-reports
  branch: master

# profilo gitlab
cnr.gitlab:
  url: http://git.si.cnr.it
  group: dev
  project: sigla-reports
  ref: formazione
  token:
```

Configurare un webhook di push del repository sull'endpoint `/api/v1/hook`: a ogni commit i report modificati vengono rimossi dalla cache e ricaricati alla richiesta successiva.

## Test

```bash
mvn test
```

I test usano JUnit 5 e i profili `common,github,test` (`src/test/resources/application*.yml`), con storage su filesystem.

## Release e CI

- **`maven.yml`**: a ogni push su `master` e `sviluppo` pubblica il pacchetto su GitHub Packages e l'immagine `:latest` su GHCR (JDK 21).
- **`release.yml`**: alla creazione di una release GitHub imposta la versione del `pom.xml` al tag, pubblica pacchetto e immagine con quel tag, poi riporta il `pom.xml` alla versione `-SNAPSHOT` e committa.

## Note di migrazione

Stato corrente rispetto alle versioni precedenti (2.x):

- **Java 21** e **Spring Boot 4.1** (prima Java 8 / Spring Boot 2.2): `javax.*` → `jakarta.*`, Spring Security 7 con `SecurityFilterChain`, Hibernate 7, JUnit 5.
- Rimossi gli artefatti `it.cnr.si.sprint` (`sprint-logging`, `sprint-storage`, `sprint-custom`) e la proprietà `sprint.version`; sostituiti da `storage-cloud`, Hazelcast e dipendenze Spring Boot dirette.
- Lo storage non usa più `cnr.fs.path`: usare `cnr.storage.driver` e le relative proprietà. Il layout dei file è cambiato (cartella radice `print.storage.base-path`).
- I lock distribuiti usano `IMap.tryLock` (Hazelcast 5) al posto di `ILock`.
- La documentazione API è generata da springdoc (al posto di Springfox).
- `jasperreports` 6.20.x: ricontrollare i report esistenti.

## Licenza

Distribuito con licenza **GNU Affero General Public License v3** – vedi il file [LICENSE](LICENSE).

Copyright (C) Consiglio Nazionale delle Ricerche.
