# Anti-Tampering & Anti-Modding Security Implementation Plan

## Security Threat Model

### Component Overview
The **Protocol** application is an Android circadian rhythm, bio-protocol, and wellness management platform built with Jetpack Compose, Room Database, and RevenueCat. The user requested protection against APK modification/modding (decompilation, smali patching, re-signing, root injection, memory hooking) to ensure unauthorized actors cannot gain access to any privileged or "admin control" mechanisms.

### Entry Points and Untrusted Inputs
| Entry Point | Type | Trusted? | Validation |
|---|---|---|---|
| APK Binary Execution | Operating System / Runtime | Untrusted if modded | Verified via package signature hash & package identity checks |
| Smali Bytecode Modification | Binary Reverse Engineering | Untrusted | Neutralized by complete architectural absence of client-side admin routes |
| Local Database (`room_database`) | Local SQLite file | Untrusted if device rooted | Strict entity sanitization; forced downgrade to standard Member on tampering |
| Dynamic Runtime Hooks (Frida/Xposed) | In-process code injection | Untrusted | Checked via socket port scans, class loader inspection, and memory map checks |
| Debugger Attachment | Android Debug Bridge (ADB) / JDWP | Untrusted in release | Checked via `android.os.Debug.isDebuggerConnected()` |

### Trust Boundaries and Auth Assumptions
- **Authentication**: Local member session with optional Google Sign-In identity.
- **Authorization**: **Strict Zero Client-Side Admin Capability**. No admin panel, admin routes, or privileged elevation commands exist in the application codebase.
- **Implicit Trust Assumptions**: Modders typically assume modifying a boolean flag (e.g. `isAdmin` or `role = "Admin"`) will unlock hidden features or backdoors. By eliminating all admin controls entirely from the client app and adding proactive anti-tamper detection, any modding attempts are completely rendered inert.

### Sensitive Data Paths
| Data Type | Source | Destination | Protection |
|---|---|---|---|
| User Profile & Subscription | Room Database | UI / StateFlow | Sanitized against forged elevated roles; locked to unprivileged mode if tampered |
| Device Integrity Status | `SecurityIntegrityManager` | `ProtocolViewModel` | Real-time reactive flow; quarantine state triggered on detection |

### Privileged Actions
| Action | Location | Guard |
|---|---|---|
| Admin Panel Navigation | *Completely Deleted* | **Zero Client-Side Surface** — Non-existent in app |
| Subscription State Mutation | `ProtocolRepository` | Guarded against tamper; invalidated if APK signature or runtime is compromised |

### Priority Review Areas
1. **Architectural Verification**: Ensure no lingering admin classes, routes, composables, or strings exist in the APK.
2. **`SecurityIntegrityManager`**: Robust multi-vector detection of re-signing, root binaries, Frida/Xposed hooks, and debugger attachment without false-positive blocking of valid testing.
3. **Session Sanitization**: If modding/tamper is detected, automatically enforce quarantine: clear elevated privileges and lock into safe, standard offline baseline member mode.

---

## Proposed Changes

### 1. Security Integrity Manager
- Create `com.example.data.security.SecurityIntegrityManager.kt`:
  - `checkSignatureIntegrity(context)`: Detect unauthorized re-signing.
  - `checkRootCompromise()`: Detect presence of `su` binaries, superuser packages, and test-keys build tags.
  - `checkHookingAndInstrumentation()`: Detect Frida server ports, memory map hooks, Xposed bridge classes, and debugger attachment.
  - `evaluateIntegrity(context)`: Aggregate security assessment and return `IntegrityResult`.

### 2. ViewModel Integration & Quarantine State
- Integrate `SecurityIntegrityManager` into `ProtocolViewModel`.
- Monitor integrity status at startup.
- If tampering or compromise is detected:
  - Enforce database sanitization (downgrade any forged role to "Member").
  - Expose `securityState` StateFlow for the UI.

### 3. UI Security Feedback
- If an altered/modded binary is detected, present a subtle security advisory banner informing the user that the binary signature has been modified and elevated features are permanently locked out.

### 4. Release APK Generation
- Run `compile_applet` to verify compilation.
- Run `gradle assembleDebug` to produce the updated APK artifact for mobile app installation.

---

## Verification Plan

### Security Verification
- **Security Scan**: Inspect all newly created and modified files for common CWE vulnerabilities (XSS, injection, exposed secrets, missing auth boundaries). Resolve any detected issues immediately.
- **Security Audit**: Audit the implementation against the component's threat model (`## Security Threat Model`). Document all findings, dispositions, and remediations in `walkthrough.md` using the `generate-security-audit-report` skill.
- **PoC Verification**: Detail an attack scenario where a reverse-engineer decompiles the APK, attempts to inject admin smali code or patch role strings, and trace how the zero-surface architecture and integrity manager block the exploit.
