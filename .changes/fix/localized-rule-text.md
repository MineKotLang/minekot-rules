# Localize rule presentation text

- Move rule names, descriptions, diagnostics, correction labels, and option descriptions into the English `MineKotRulesBundle.properties` language resource.
- Centralize stable rule IDs and deprecated aliases in `RuleContracts` so visitor implementations do not repeat protocol literals.
- Add a source-level regression test that rejects inline presentation strings in production rules.
