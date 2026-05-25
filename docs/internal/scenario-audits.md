# Scenario-Based Audits

MyStem4j documentation and API audits should be scenario-based. The auditor should
act like a user who has a concrete problem and only cares whether MyStem4j helps
solve that problem with a clear, safe path.

Do not run broad documentation audits that ask for every missing page, release
process detail, maintainer workflow, or general open-source project convention.
Those audits produce noise. They should be used only when the project explicitly
needs that kind of review.

## Auditor Rules

Each auditor gets one user scenario and starts from `README.md`.

The auditor should:

- navigate only through links that seem useful for solving the scenario;
- read implementation code only when needed to verify that documented API usage is
  real and safe;
- ignore publication, release, CI, maintainer, and internal-process concerns unless
  they block the scenario;
- report pages actually visited;
- report only problems that block the task, make the task unsafe, or force the user
  to understand irrelevant details.

Each finding should state:

- the scenario step that was blocked or made risky;
- the page and heading where it happened;
- the smallest useful documentation or API change.

## Core Scenarios

### Runtime Request

User goal: from Java, call MyStem for one text and print raw JSON output.

The audit should verify executable configuration, minimal dependency setup,
runtime client construction, output format, resource closing, and what to do for
one-shot versus reusable or pooled clients.

### Parsed Model

User goal: turn MyStem JSON into Java objects with lemmas, grammar, and offsets.

The audit should verify the dependency path, parser usage, required MyStem output
format, handling of text issues, and when Unicode preparation is needed.

### Search Tokens

User goal: convert parsed MyStem output into search-ready forms without using
Lucene directly.

The audit should verify tokenizer dependency setup, preset choice, offset
requirements, token forms, and whether the docs explain what the consumer should
do with the resulting tokens.

### Lucene Integration

User goal: use MyStem in a Lucene analyzer for indexing and query analysis.

The audit should verify analyzer setup, JSON client configuration, lifecycle,
threading/concurrency, token offsets, position behavior, and which tokenization
policy to choose.

### Gradle MyStem Preparation

User goal: make a Gradle build prepare a MyStem executable for tests or a local
application distribution.

The audit should verify plugin setup, license opt-in, download/probe workflow,
test property wiring, distribution copy behavior, and executable path usage.

## Prompt Template

```text
Ты внешний пользователь, а не ревьюер документации.

Твоя задача: решить конкретную проблему:
<scenario>.

Стартуй только с README.md. Переходи только по ссылкам, которые кажутся нужными
для решения этой задачи. Не оценивай документацию на полноту.

Не требуй разделы про публикацию, release process, maintainer workflow, CI,
troubleshooting или architecture, если они не нужны, чтобы выполнить задачу.

Можно читать исходный код и тесты только для проверки того, что документированный
API реально существует и безопасен для сценария. Не проводи общий code review.

Фиксируй только:
- где ты не понял следующий шаг;
- где пример нельзя скопировать для твоей задачи;
- где не хватает зависимости/import/configuration именно для твоего сценария;
- где текст заставляет читать лишние детали вместо решения задачи;
- где можно сделать опасную ошибку: утечка процесса, неправильный lifecycle,
  неверные offsets, небезопасная concurrency модель.

Для каждой проблемы укажи:
- какая задача была заблокирована;
- на какой странице это случилось;
- что именно надо изменить минимально.

Отдельно перечисли страницы, которые ты реально посетил.
```
