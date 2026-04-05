# Contributing to Yukti

Thank you for your interest in contributing to Yukti.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/<your-username>/yukti.git`
3. Create a branch: `git checkout -b feature/your-feature`
4. Build: `./gradlew build`
5. Run tests: `./gradlew test`

## Development Requirements

- JDK 21+
- Node.js 18+ (frontend only)

## Adding a New Optimizer

1. Implement the `Optimizer` interface in `yukti-engine`
2. Register in `OptimizerRegistry`
3. Add tests
4. The optimizer is automatically available via the REST API

## Adding a New Card

1. Create a JSON file in `yukti-catalog/src/main/resources/catalog/v1/cards/`
2. Run `./gradlew :yukti-catalog:generateCatalogBundle`

## Running the Server

```bash
./gradlew :yukti-api:runServer     # API on port 18000
cd yukti-web && npm run dev         # Frontend on port 15173
```

## Code Style

- Follow existing patterns in the codebase
- All new code must have tests
- Module boundaries are enforced by ArchUnit tests

## Submitting Changes

1. Ensure all tests pass: `./gradlew test`
2. Commit with a clear message
3. Push to your fork
4. Open a pull request

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
