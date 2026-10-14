# TerraLatitude development

- Minecraft 1.21.1, Java 21, NeoForge 21.1.257; exact FTF and Natural Temperature
  binaries are pinned by the offline build. Read README.md and
  docs/CLIMATE-DESIGN.md before changing integration behavior.
- The owner performs in-world testing. Do not launch Minecraft, a dedicated
  server, or a world unless explicitly requested. Compile, static inspection,
  pure-model tests, and package validation are appropriate.
- Build with `python3 build.py`; verify with `python3 verify.py`. Local dependency
  paths belong in ignored build.local.toml; never bundle upstream mod/game JARs.
- Keep current baseline behavior distinct from proposed integrated climate mode.
  A future single-pass climate model must avoid double altitude correction and
  duplicate Natural Temperature application.
- Preserve generator/dimension ownership checks, original non-temperature climate
  coordinates, zero-influence pass-through, cave-threshold semantics, deterministic
  output, and generation-thread safety unless deliberately changing a documented
  contract with appropriate tests.
- Never install a JAR or alter active pack configs as part of a build. Treat
  installation as a separate action. Do not combine the old fnf_altitude prototype
  with TerraLatitude.
- Report offline results separately from runtime verification. Compilation does
  not prove successful Mixin application or complete pack compatibility.
