# allium-deck-wasm

Browser WebAssembly bindings for the allium-deck Project Sekai recommendation
engine. Callers provide master data and a user collection; no game data is bundled.

## Business-rule source

JP7 business rules were ported from
**[Team-Haruki/sekai-deck-recommend-cpp](https://github.com/Team-Haruki/sekai-deck-recommend-cpp)**
at [`496caed78a07ffe1f2d1f1553047dbf6b2ed0314`](https://github.com/Team-Haruki/sekai-deck-recommend-cpp/commit/496caed78a07ffe1f2d1f1553047dbf6b2ed0314),
including area-item, MySEKAI gate and World Link finale rules. They are implemented
in allium-deck's Rust engine. Master-table shuffle overrides and browser
integration cases also draw on Moesekai's published patches.

See the [source map](https://github.com/empty-sekai/allium-deck/blob/v0.1.0/docs/game-rule-sources.md)
for attribution and behavior differences, and the
[parameter reference](https://github.com/empty-sekai/allium-deck/blob/v0.1.0/docs/parameters.md)
for input and completion contracts. A timed-out search does not certify complete
Top-K.

## License

MIT OR Apache-2.0. Both license texts are included in the package.
