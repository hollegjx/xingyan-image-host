# AI recovery simulation

Created 2026-10-29. The local mock follows this repository's Qwen adapter contract and failure shapes, not a provider-owned mock. Owner search was unavailable because Postman CLI was signed out; nothing was pushed or deployed.

## Purpose

Exercise the real worker, task store, recovery service, HTTP adapter and result reader without paid calls. The sole member models provider generation and result downloads; H2 runs in the test process. See the handler and `AiRecoveryHttpSimulationTest` for modeled requests.

## Execution

From the repository root run `postman simulation run postman/simulations/ai-recovery.sim.yaml --port auto --no-history --output ndjson`. Read the port from its `listening` event. In `backend`, run `AI_SIMULATION_URL=http://127.0.0.1:<port> mvn -Dtest=AiRecoveryHttpSimulationTest test`. Stop the simulator with Ctrl+C after reading its request events. For a full run set the same variable while running `mvn verify`.

## Assumptions

- 2026-10-29: the test redirects transport connections to loopback only; production HTTPS host validation remains active before the result connection is opened. This does not test public TLS, cloud ACLs, actual charging, or provider image quality (observed local run).
- 2026-10-29: every scenario submits generation once; manual reconciliation never submits generation. HTTP 500 JSON, 503 HTML, dropped connections, 403 rejection, unavailable saved result, and successful saved receipt were exercised (observed test assertions and request log).
- 2026-10-29: the socket-drop scenario has no completed request event, so the handler's namespaced counter and the test assertion prove it received exactly one generation attempt. A long-running socket timeout was not simulated (observed local run).

## Observations

<!-- begin:observations -->
2026-10-29: the pre-push full Maven verify rerun used this simulation; ai-provider served generation and result traffic and no member was silent. Successful receipt recovery downloaded a PNG and changed UNKNOWN to SUCCEEDED without a second POST. An unavailable result stayed UNKNOWN. Request counters also confirmed no retry after a dropped connection. The simulator was stopped after the run.
<!-- end:observations -->

## What didn't work

- 2026-10-29: provider workspace discovery returned `No authorization data found`; the local mock was built from the existing adapter instead. CLI 1.80.0 ran it locally without login or cloud history.
