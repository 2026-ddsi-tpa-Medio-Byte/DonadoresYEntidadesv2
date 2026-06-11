exclude: ^catedra/

repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.6.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-yaml
      - id: check-added-large-files
      - id: check-json
      - id: check-xml
      - id: pretty-format-json
        args: [--autofix, --indent=4]

  - repo: https://github.com/collective/zpretty
    rev: 3.1.0
    hooks:
      - id: zpretty

  - repo: local
    hooks:
      - id: spotless
        name: Spotless Apply
        entry: mvn spotless:apply
        language: system
        pass_filenames: false

      - id: maven-verify
        name: Maven Verify (Checkstyle + PMD + SpotBugs)
        entry: mvn -q verify
        language: system
        pass_filenames: false
