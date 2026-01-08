# Module c2pa-android

C2PA Android is a Kotlin wrapper around the C2PA C API, providing content authenticity and provenance functionality for Android applications.

## Overview

This library enables Android applications to create, read, and validate C2PA manifests, which provide cryptographic proof of content origin and history. The library uses JNI to bridge native C2PA libraries with Android's Kotlin/Java ecosystem.

## Core Components

### Content Authenticity

- [Reader] - Read and validate C2PA manifests from media files
- [Builder] - Create new C2PA manifests with claims, assertions, and ingredients

### Signing Methods

The library supports multiple signing approaches:

- **Direct signing** - Sign with in-memory private keys using [SignerInfo]
- **Callback signing** - Implement custom signing logic with [Signer]
- **Web service signing** - Delegate signing to remote servers with [WebServiceSigner]
- **Hardware security** - Use device hardware security modules with [StrongBoxSigner] or [KeyStoreSigner]

### Hardware Security Integration

- [StrongBoxSigner] - Hardware-backed signing using Android StrongBox
- [KeyStoreSigner] - Android Keystore signing with optional biometric authentication
- [CertificateManager] - Certificate generation and management for Android Keystore

## Platform Requirements

- **Minimum Android SDK**: 28 (Android 9.0 Pie)
- **Target Android SDK**: 35
- **Kotlin**: 1.9+
- **Hardware security** (optional): Devices with StrongBox or TEE support
