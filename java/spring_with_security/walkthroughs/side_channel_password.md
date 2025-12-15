# Side-Channel Attacks in Password Hashing

## What Are Side-Channel Attacks?

Side-channel attacks exploit **indirect information** leaked during computation, rather than attacking the algorithm directly.

### Common Side-Channel Attack Types

| Attack Type | What It Exploits | Example |
|-------------|------------------|---------|
| **Timing Attack** | Execution time variations | Measure how long hash verification takes |
| **Cache-Timing Attack** | CPU cache access patterns | Detect which memory was accessed |
| **Power Analysis** | Power consumption patterns | Monitor CPU power draw during hashing |
| **Electromagnetic** | EM radiation from circuits | Capture radio emissions from CPU |
| **Acoustic** | Sound from CPU operations | Listen to CPU coil whine |

## Why This Matters for Password Hashing

### The Problem: Data-Dependent Memory Access

Some algorithms access memory based on **the data being processed** (the password). An attacker can potentially:

1. **Observe which memory locations are accessed**
2. **Infer information about the password** from access patterns
3. **Reduce the search space** for brute-force attacks

### Real-World Scenario

```
┌─────────────────────────────────────────┐
│  User logs in from shared server/VM     │
│  Attacker has access to same hardware   │
└──────────────┬──────────────────────────┘
               │
    ┌──────────▼───────────┐
    │  Password Hashing    │
    │  Memory Access:      │
    │  [A][B][C][D][E][F]  │
    └──────────┬───────────┘
               │
    ┌──────────▼───────────┐
    │  Attacker monitors   │
    │  CPU cache activity  │
    │  "Aha! Location C    │
    │   was accessed!"     │
    └──────────────────────┘
```

**Vulnerable Environments:**
- ☁️ **Cloud servers** (shared hardware)
- 🖥️ **Virtual machines** (VM escape vulnerabilities)
- 📱 **Mobile devices** (malicious apps monitoring)
- 🏢 **Multi-tenant systems** (shared hosting)

---

## Argon2 Variants: The Solution

### Argon2d - Data-Dependent (Fast but Vulnerable)

```
Memory access pattern depends on password content
→ Faster
→ More resistant to time-memory trade-offs
→ VULNERABLE to side-channel attacks
```

**How it works:**
```python
# Pseudocode - simplified
def argon2d(password):
    state = initial_hash(password)
    
    for iteration in range(iterations):
        # Memory address depends on PASSWORD DATA
        address = calculate_address(state)  # ← Uses password bits
        state = mix(state, memory[address])
    
    return state
```

**Problem:** An attacker monitoring cache can see which memory addresses were accessed and potentially deduce password characteristics.

**Use case:** Offline password cracking tools (where attacker has full control)

---

### Argon2i - Data-Independent (Slow but Secure)

```
Memory access pattern is INDEPENDENT of password
→ Slower
→ Resistant to side-channel attacks
→ Slightly weaker against time-memory trade-offs
```

**How it works:**
```python
# Pseudocode - simplified
def argon2i(password):
    state = initial_hash(password)
    
    for iteration in range(iterations):
        # Memory address depends ONLY on iteration number
        address = calculate_address(iteration)  # ← Predictable, no password data
        state = mix(state, memory[address])
    
    return state
```

**Benefit:** Memory access pattern is the same for ALL passwords. Attacker learns nothing from monitoring.

**Use case:** Server-side password hashing (where side-channels are a concern)

---

### Argon2id - Hybrid (Best of Both Worlds) ⭐

```
First half: Data-independent (like Argon2i)
Second half: Data-dependent (like Argon2d)
→ Balanced performance
→ Resistant to side-channel attacks
→ Resistant to time-memory trade-offs
```

**How it works:**
```python
# Pseudocode - simplified
def argon2id(password):
    state = initial_hash(password)
    
    # FIRST HALF: Data-independent (side-channel resistant)
    for iteration in range(iterations // 2):
        address = calculate_address(iteration)  # Predictable
        state = mix(state, memory[address])
    
    # SECOND HALF: Data-dependent (faster, more secure)
    for iteration in range(iterations // 2, iterations):
        address = calculate_address(state)  # Uses password data
        state = mix(state, memory[address])
    
    return state
```

**Why this works:**
1. **First half protects** against side-channel attacks (data-independent)
2. **Second half optimizes** for security against time-memory trade-offs (data-dependent)
3. **Best compromise** for general-purpose use

---

## Practical Attack Example: Cache-Timing Attack

### Attack Setup
```
┌────────────────────────────────────┐
│  Cloud Server (AWS/Azure/GCP)      │
│  ┌─────────────┐  ┌──────────────┐│
│  │ Your App    │  │ Attacker VM  ││
│  │ (Victim)    │  │              ││
│  └──────┬──────┘  └──────┬───────┘│
│         │                │        │
│    ┌────▼────────────────▼─────┐  │
│    │   Shared CPU Cache        │  │
│    │   [L1] [L2] [L3]          │  │
│    └───────────────────────────┘  │
└────────────────────────────────────┘
```

### Attack Steps with Argon2d (VULNERABLE):

```
1. Attacker fills CPU cache with known data
2. User attempts login → Argon2d hashes password
3. Argon2d accesses memory based on password bits
4. Attacker measures cache access times
5. Slow access = cache miss = that memory was used
6. Fast access = cache hit = that memory was NOT used
7. Attacker learns password-dependent access pattern
8. Reduces password search space
```

### Defense with Argon2id/Argon2i (SECURE):

```
1. Attacker fills CPU cache with known data
2. User attempts login → Argon2id hashes password
3. Argon2id accesses memory in PREDICTABLE pattern (first half)
4. Attacker measures cache access times
5. Pattern is SAME for ALL passwords
6. Attacker learns NOTHING useful
7. Full brute-force still required ✅
```

---

## Real-World Impact

### Scenario 1: Web Application on Cloud

```java
// Your application uses Argon2id
@Bean
public PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); // Uses Argon2id
}
```

**Threats mitigated:**
- ✅ Other VMs on same physical server can't spy on password hashing
- ✅ Spectre/Meltdown-like CPU vulnerabilities have less impact
- ✅ Malicious cloud provider employees can't extract password patterns

### Scenario 2: Mobile Application

```kotlin
// Mobile app doing local password verification
val encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
```

**Threats mitigated:**
- ✅ Malicious apps can't monitor memory access patterns
- ✅ Rooted devices with memory inspectors learn less
- ✅ Hardware debuggers can't easily extract password info

### Scenario 3: Enterprise Server (Low Risk)

```java
// Isolated server, no multi-tenancy
// Even Argon2d would be acceptable here, but Argon2id is still better default
```

---

## Performance Comparison

```
Password: "SecurePass123!"
Hardware: Intel i7, 16GB RAM

Algorithm    | Time (ms) | Side-Channel Safe? | Recommended?
-------------|-----------|-------------------|-------------
Argon2d      | 50        | ❌ NO             | Only offline tools
Argon2i      | 65        | ✅ YES            | ⚠️ Acceptable
Argon2id     | 55        | ✅ YES            | ⭐ BEST CHOICE
BCrypt       | 80        | ⚠️ VARIES         | Outdated
```

*Note: Times are approximate and depend on parameters*

---

## When to Use Each Variant

### Use Argon2id (DEFAULT) ⭐
```java
Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
```
- ✅ Web applications
- ✅ Mobile apps
- ✅ Cloud services
- ✅ Multi-tenant systems
- ✅ **Any general-purpose use**

### Use Argon2i (PARANOID)
```java
// Only if maximum side-channel protection needed
// Slightly slower, very rare use case
```
- Government/military applications
- Extremely sensitive data
- Known side-channel threats

### Use Argon2d (NEVER in production)
```
DON'T USE for password hashing in applications!
```
- Only for cryptocurrency mining
- Offline password crackers (where you control hardware)

---

## Key Takeaways

1. **Side-channel attacks exploit indirect information** (timing, cache, power)
2. **Argon2d is vulnerable** because memory access depends on password data
3. **Argon2i is safe** because memory access is predictable
4. **Argon2id combines both** - safe AND fast
5. **Cloud/multi-tenant environments** make side-channels a real concern
6. **Spring Security defaults to Argon2id** - perfect choice!

## Bottom Line

```java
@Bean
public PasswordEncoder passwordEncoder() {
    // This uses Argon2id by default ✅
    // You're protected against side-channel attacks
    // No additional configuration needed
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
}
```

**You're already using the best option!** The side-channel protection is automatic with Argon2id. 🔒

---

## Further Reading

- [Argon2 RFC 9106](https://datatracker.ietf.org/doc/html/rfc9106)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Side-Channel Attacks on Cryptographic Software](https://www.schneier.com/academic/paperfiles/paper-cache-timing.pdf)
- [Password Hashing Competition](https://www.password-hashing.net/)
