# StudyMind

StudyMind is a distributed AI study platform where users upload PDFs or YouTube links and immediately turn passive content into active learning.
It uses Retrieval-Augmented Generation (RAG) to answer questions, generate summaries, create quizzes, and build flashcards based only on user-owned material.

## Product Value

- Upload a PDF or YouTube lesson and chat with the content
- Get grounded answers with source chunks and video timestamps
- Generate study assets (summaries, quizzes, flashcards)
- Track progress over time

## Architecture (Microservices from Day 1)

```mermaid
flowchart TD
  user[StudentUser] --> spa[AngularSPA]
  spa --> gateway[ApiGatewayAuthService]
  gateway --> docsvc[DocumentService]
  gateway --> vidsvc[VideoService]
  gateway --> chatsvc[ChatLlmService]

  gateway --> authdb[(AuthDB)]
  docsvc --> docdb[(DocumentDB)]
  vidsvc --> viddb[(VideoDB)]
  chatsvc --> chatdb[(ChatDB)]

  docsvc --> mq[(RabbitMQ)]
  vidsvc --> mq
  chatsvc --> mq

  mq --> proc[ProcessingWorkerPython]
  mq --> emb[EmbeddingWorkerPython]
  mq --> tr[TranscriptionWorkerPython]

  proc --> docdb
  proc --> mq
  emb --> qdrant[(Qdrant)]
  emb --> docdb
  emb --> viddb
  tr --> viddb
  tr --> mq

  emb --> openai[OpenAIEmbeddings]
  tr --> whisper[WhisperAPI]
  chatsvc --> claude[AnthropicClaude]
```



## Tech Stack


| Layer         | Choice                                          |
| ------------- | ----------------------------------------------- |
| Core services | Java 21 + Spring Boot 3 (4 services)            |
| Frontend      | Angular 17+ + PrimeNG + Signals                 |
| Workers       | Python 3.11 (consumer workers)                  |
| Relational DB | PostgreSQL 16                                   |
| Vector DB     | Qdrant                                          |
| Queue         | RabbitMQ (Kafka-ready abstraction)              |
| Cache         | Redis 7                                         |
| File Storage  | MinIO (dev) / S3 (prod)                         |
| AI Providers  | Anthropic Claude + OpenAI Embeddings + Whisper  |
| Infra         | Docker Compose (MVP), Kubernetes optional later |


## Repository Structure

```text
StudyMind/
├── README.md
└── docs/
    ├── architecture/
    │   ├── ARCHITECTURE.md
    │   ├── DATA-MODEL.md
    │   ├── EVENTS.md
    │   └── adr/
    ├── development/
    │   ├── 00-environment-setup.md
    │   ├── 01-api-gateway-auth.md
    │   ├── 02-document-service.md
    │   ├── 03-processing-worker.md
    │   ├── 04-embedding-worker.md
    │   ├── 05-chat-rag-service.md
    │   ├── 06-video-service.md
    │   ├── 07-transcription-worker.md
    │   ├── 08-llm-service.md
    │   ├── 09-quiz-flashcard-service.md
    │   ├── 10-frontend.md
    │   └── 11-observability-deploy.md
    └── DEPLOYMENT.md
```

## Quick Start (Documentation-First Setup)

1. Clone and enter project:
  - `git clone <your-repo-url>`
  - `cd StudyMind`
2. Read setup guide:
  - `docs/development/00-environment-setup.md`
3. Bring infra up:
  - `docker compose up -d`
4. Configure environment variables:
  - copy `.env.example` to `.env` per setup guide
5. Implement in order:
  - follow numbered files in `docs/development/`

## Roadmap

- Phase 1: PDF upload + asynchronous indexing + chat RAG
- Phase 2: YouTube ingestion + transcription + timestamped answers
- Phase 3: Summaries + quizzes + flashcards + progress tracking
- Phase 4: Observability + reliability + deployment hardening

## Documentation Map

- Core architecture: `docs/architecture/ARCHITECTURE.md`
- Data and storage model: `docs/architecture/DATA-MODEL.md`
- Event contracts and reliability: `docs/architecture/EVENTS.md`
- Design decisions (ADRs): `docs/architecture/adr/`
- Step-by-step build guide: `docs/development/`
- Production deployment: `docs/DEPLOYMENT.md`

## Portfolio Positioning

This project demonstrates:

- microservices-first architecture with clear service ownership
- event-driven architecture with asynchronous workers
- real RAG system with vector search and tenant filtering
- multi-provider AI integration with structured outputs
- reliability patterns (idempotency, retries, DLQ)
- production-oriented observability and deployment practices

