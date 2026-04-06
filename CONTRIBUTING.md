# Contributing to Meshrabiya

Thank you for your interest in contributing to Meshrabiya! This document provides guidelines and instructions for contributing.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Coding Standards](#coding-standards)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)
- [Reporting Issues](#reporting-issues)

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment. Please be considerate of others and follow standard open-source community guidelines.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Create a feature branch
4. Make your changes
5. Submit a pull request

## Development Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 35
- Gradle 8.x (included via wrapper)

### Building the Project

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/Meshrabiya.git
cd Meshrabiya

# Build the project
./gradlew build

# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint
```

### Running the Test App

1. Open the project in Android Studio
2. Select the `test-app` run configuration
3. Run on a physical device (emulator has limited WiFi support)

**Note:** WiFi Direct and Local Only Hotspot features require a physical device.

## Project Structure

```
Meshrabiya/
├── lib-meshrabiya/           # Core library module
│   └── src/
│       ├── main/             # Main source code
│       │   └── java/com/ustadmobile/meshrabiya/
│       │       ├── vnet/     # Virtual networking
│       │       │   ├── wifi/     # WiFi management
│       │       │   ├── bluetooth/# Bluetooth support
│       │       │   ├── socket/   # Chain socket implementation
│       │       │   └── datagram/ # UDP support
│       │       ├── mmcp/     # Mesh Control Protocol
│       │       ├── portforward/  # Port forwarding
│       │       └── log/      # Logging utilities
│       ├── test/             # Unit tests
│       └── androidTest/      # Instrumented tests
├── test-app/                 # Test application
├── test-shared/              # Shared test utilities
└── doc/                      # Documentation
```

## Coding Standards

### Kotlin Style

- Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and reasonably sized

### Documentation

- Add KDoc for all public classes, functions, and properties
- Include usage examples in documentation
- Update README.md for user-facing changes
- Update ARCHITECTURE.md for structural changes

Example KDoc:

```kotlin
/**
 * Routes a virtual packet to its destination.
 *
 * This method handles both unicast and broadcast packets. For unicast
 * packets, it looks up the next hop using the originator message table.
 * For broadcast packets, it forwards to all neighbors except the sender.
 *
 * @param packet The virtual packet to route
 * @param datagramPacket The underlying datagram packet (if available)
 * @param virtualNodeDatagramSocket The socket to use for forwarding
 *
 * @throws RoutingException if the packet cannot be routed
 */
fun route(
    packet: VirtualPacket,
    datagramPacket: DatagramPacket? = null,
    virtualNodeDatagramSocket: VirtualNodeDatagramSocket? = null
) {
    // Implementation
}
```

### Error Handling

- Use specific exception types
- Provide meaningful error messages
- Log errors appropriately
- Handle edge cases gracefully

## Testing

### Unit Tests

```bash
# Run all unit tests
./gradlew test

# Run tests for a specific module
./gradlew :lib-meshrabiya:test
```

### Instrumented Tests

```bash
# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

### Test Naming Convention

```kotlin
@Test
fun `should route packet to next hop when destination is known`() {
    // Test implementation
}

@Test
fun `should drop packet when hop count exceeds maximum`() {
    // Test implementation
}
```

## Pull Request Process

1. **Create a Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make Your Changes**
   - Write clean, documented code
   - Add tests for new functionality
   - Update documentation as needed

3. **Run Checks**
   ```bash
   ./gradlew build test lint
   ```

4. **Commit Your Changes**
   - Use clear commit messages
   - Reference related issues

   ```
   feat: add support for WiFi 6E band selection
   
   Adds support for the 6GHz band on Android 13+ devices that
   support WiFi 6E. This enables higher throughput and lower
   latency when available.
   
   Fixes #123
   ```

5. **Push and Create PR**
   ```bash
   git push origin feature/your-feature-name
   ```
   Then create a pull request on GitHub.

6. **Code Review**
   - Address review feedback
   - Keep the PR focused
   - Respond promptly to comments

### PR Checklist

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Lint checks pass
- [ ] Documentation updated
- [ ] CHANGELOG.md updated (if applicable)
- [ ] Commit messages are clear

## Reporting Issues

### Bug Reports

When reporting bugs, please include:

1. **Device Information**
   - Device model
   - Android version
   - Meshrabiya version

2. **Steps to Reproduce**
   - Clear, numbered steps
   - Expected vs actual behavior

3. **Logs**
   - Logcat output (if relevant)
   - Screenshots (if applicable)

### Feature Requests

For feature requests, please describe:

1. **The Problem**
   - What limitation are you facing?

2. **Proposed Solution**
   - How would you like it to work?

3. **Alternatives Considered**
   - Any alternative solutions you've thought of

## Getting Help

- **Matrix Chat**: [#meshrabiya:matrix.org](https://matrix.to/#/#meshrabiya:matrix.org)
- **GitHub Issues**: For bug reports and feature requests
- **GitHub Discussions**: For questions and general discussion

## License

By contributing to Meshrabiya, you agree that your contributions will be licensed under the same license as the project (see LICENSE file).

---

Thank you for contributing to Meshrabiya! 🎉