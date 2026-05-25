# Спецификация компонента Lucene integration

Статус: baseline 0.1
Дата: 2026-05-25

## Назначение

`mystem4j-lucene` адаптирует runtime/model/tokenization слои MyStem4j к Lucene `Analyzer`/`Tokenizer` API.

## Граница ответственности

`mystem4j-lucene` должен:

- принимать готовый `MystemClient`, настроенный на JSON output;
- читать Lucene `Reader` целиком для одного анализа поля;
- применять `MystemTextPreprocessor` перед вызовом MyStem;
- парсить JSON через `MystemJsonParser` с mapping обратно к original offsets;
- использовать `MystemSearchTokenizer` и `MystemSearchTokenizerOptions`;
- эмитить Lucene attributes с original Java UTF-16 offsets;
- не управлять загрузкой бинаря MyStem.

`mystem4j-lucene` не должен:

- зависеть от конкретного способа создания MyStem клиента;
- скрыто включать entity-aware URL/email/currency поведение;
- закрывать переданный клиент без явного opt-in;
- поддерживать Lucene 10.x при Java 17 baseline.

## Compatibility

Baseline:

- Java: 17
- Lucene: 9.12.3

Lucene 10.x требует Java 21, поэтому он не является целевой версией до поднятия Java baseline всего проекта.

## API

Public classes:

- `MystemLuceneAnalyzer`
- `MystemLuceneTokenizer`

Analyzer constructors:

```java
MystemLuceneAnalyzer(MystemClient client)
MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options)
MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, boolean closeClientOnClose)
```

Tokenizer constructors:

```java
MystemLuceneTokenizer(MystemClient client)
MystemLuceneTokenizer(MystemClient client, MystemSearchTokenizerOptions options)
```

## Token emission

Lucene token stream должен эмитить:

- `CharTermAttribute` - form text;
- `OffsetAttribute` - original Java UTF-16 offsets;
- `PositionIncrementAttribute` - `1` для первой form token, `0` для дополнительных forms того же source token;
- `TypeAttribute` - lowercase search token type;
- `KeywordAttribute` - значение из `MystemTokenForm.keyword()`.

`SEPARATOR` и `OTHER` search-token types не эмитятся.

## Runtime policy

Для индексирования рекомендуется pooled `MystemClient`.

Reusable single-session client не является хорошим default для конкурентного Lucene indexing. One-shot client корректен, но обычно дороже по startup overhead.

## Acceptance criteria

- Есть модуль `mystem4j-lucene`.
- Модуль зависит от Lucene 9.x и не поднимает Java baseline выше 17.
- Есть `Analyzer` и `Tokenizer`.
- Default analyzer использует conservative tokenization policy.
- Entity-aware behavior включается только через explicit `MystemSearchTokenizerOptions`.
- Unit tests проходят без реального MyStem через fake client.
- Offsets в Lucene token stream соответствуют исходному input, включая input, подготовленный `MystemTextPreprocessor`.
