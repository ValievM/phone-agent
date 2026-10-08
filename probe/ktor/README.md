# Ktor SSE probe

Temporary JVM-only experiment for issue #96. It is not a dependency of `:app` or any release variant. The probe compiles the production SSE parser, event mapper, and DTO sources directly so their implementation is reused without changing `:data:llm`; remove this module and its migration-plan section when the experiment is reverted.

The CIO engine is selected because Ktor provides it for both Android and JVM targets, unlike a JVM-only transport engine.
