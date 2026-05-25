# Спецификация компонента search-token preparation

Статус: baseline 0.1 для промежуточного слоя перед Lucene
Дата: 2026-05-24

## Назначение

`mystem4j-tokenization` преобразует `MystemDocument` в search-oriented токены, пригодные для будущего Lucene-слоя.

Компонент находится между `mystem4j-model` и будущим `mystem4j-lucene`.

## Граница ответственности

`mystem4j-tokenization` должен:

- принимать только parsed model objects, а не raw CLI output;
- сохранять offsets в координатах исходной Java-строки;
- формировать набор search forms для каждого токена;
- классифицировать токены на coarse типы;
- учитывать известные MyStem surface quirks, влияющие на поиск;
- синтезировать gap tokens из исходного текста, если MyStem не вернул часть input.
- отделять обязательную offset-safety логику от опционального semantic enrichment для чисел, URL, email и валют.

`mystem4j-tokenization` не должен:

- запускать MyStem CLI;
- зависеть от Lucene;
- менять `MystemDocument`;
- принимать токены с неизвестными offsets.

## API

```java
List<MystemSearchToken> tokenize(MystemDocument document);
```

Tokenizer создается с `MystemSearchTokenizerOptions`.

Default:

```java
new MystemSearchTokenizer()
```

эквивалентен `MystemSearchTokenizerOptions.conservative()`.

Presets:

- `conservative()` - safe offsets, gaps, suffix recovery, lemmas и fallback forms без semantic entity classification;
- `search()` - дополнительно выделяет numbers и currency symbols как token types, но не делает URL/email merge и currency expansion;
- `entityAware()` - включает legacy/rich behavior: numbers, URL/email merge, currency classification и currency expansion.

Выходные типы:

- `MystemSearchToken`
- `MystemTokenForm`
- `MystemSearchTokenType`

## Token types

Baseline типы:

- `WORD`
- `NUMBER`
- `URL`
- `EMAIL`
- `CURRENCY`
- `SEPARATOR`
- `OTHER`

## Forms

Правила baseline:

- token с lemma получает lemma forms с `keyword=true`;
- token без lemma, но с type `WORD`, получает lowercase source form с `keyword=false`;
- numeric token получает lowercase source form с `keyword=true`; при conservative policy он может быть публично представлен как `WORD`, а не `NUMBER`;
- `+`, `++`, `#` suffixes дают suffixed и suffixless forms;
- URL дает full URL и domain forms только при включенном URL merge;
- email дает full email и domain forms только при включенном email merge;
- currency symbol дает symbol, known localized names и ISO code forms только при включенном currency expansion;
- exceptional diacritics `U+0301` и `U+0341` удаляются из fallback word/number forms.

## MyStem quirks

Baseline должен покрывать:

- soft hyphen в исходном слове, удаленный MyStem из surface text;
- `+`, `++`, `#`, которые MyStem может оставить в lemma, но убрать из token text;
- URL/email, разбитые MyStem на несколько токенов, при включенном entity-aware policy;
- currency symbols как самостоятельные search tokens при включенной currency policy;
- fallback behavior для нерусских слов без lemma;
- legacy Unicode character groups, найденные в старой реализации как отличающиеся от обычной Java-классификации
  `Character.getType(...)`.

## Gap synthesis

MyStem может не вернуть в JSON числа, валюты, punctuation URL/email и другие фрагменты, если запрос сделан без `copyInput`. Tokenization layer должен проходить по gaps между соседними `MystemToken` ranges и создавать search tokens из исходного текста.

Baseline gap tokenizer должен распознавать:

- whitespace/control separators;
- number-like runs, включая `+`, `#`, `.`; comma, slash, dash, percent and currency-adjacent forms do not make the whole run a `NUMBER`;
- currency symbols;
- URL glue `://`;
- email glue `@`;
- word-like runs;
- single fallback `OTHER` tokens.

## Acceptance criteria

- Есть модуль `mystem4j-tokenization`.
- Модуль зависит от `mystem4j-model`, но не зависит от `mystem4j-runtime` напрямую.
- Токенизация сохраняет original UTF-16 offsets.
- Токен с unknown offsets дает `MystemTokenizationException`.
- Gaps исходного текста не теряются.
- Есть тесты для soft hyphen, `+`/`++`/`#`, URL/email, currencies и exceptional diacritics.
- Default tokenizer не включает entity-aware семантику без явных options.
- Есть opt-in integration test с реальным MyStem, который проверяет восстановление tokens, пропущенных без `copyInput`.
