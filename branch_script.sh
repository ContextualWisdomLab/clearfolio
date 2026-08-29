git checkout -b sentinel/fix-secrets-injection
git add .
git commit -m "🛡️ Sentinel: [CRITICAL] Fix secrets injection vulnerability

🚨 Severity: CRITICAL
💡 Vulnerability: Runtime secrets were injected via environment variables using Spring placeholders, which risks exposing them.
🎯 Impact: Attackers could read sensitive HMAC and artifact token secrets if they gain access to the environment.
🔧 Fix: Removed environment variable fallbacks and default empty strings from `@Value` annotations, enforcing KV-based config tree lookups.
✅ Verification: Ran `mvn clean verify` to ensure tests pass with the fail-fast startup behavior.
"
