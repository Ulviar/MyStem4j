# Спецификация компонента постобработки MyStem

Статус: baseline 0.1 для второго компонента
Дата: 2026-05-23

## Назначение

`mystem4j-model` отвечает за преобразование сырого вывода MyStem в типизированные структуры данных и за подготовку текста к безопасной работе с MyStem без потери offset-информации, нужной будущему Lucene-слою.

Компонент находится между `mystem4j-runtime` и будущим `mystem4j-lucene`.

## Граница ответственности

`mystem4j-model` должен:

- парсить JSON-вывод MyStem 3.1;
- представлять токены, леммы, грамматические разборы, веса и граммемы типизированно;
- выравнивать элементы MyStem `text` с исходной Java-строкой;
- возвращать offsets в Java UTF-16 code units, совместимых с Lucene offsets;
- фиксировать нефатальные проблемы выравнивания;
- готовить входной текст к MyStem, заменяя опасные Unicode-последовательности и сохраняя mapping к оригиналу;
- оставаться независимым от Lucene.

`mystem4j-model` не должен:

- запускать MyStem CLI;
- скачивать бинарь MyStem;
- зависеть от Lucene;
- выбирать единственную "правильную" лемму за пользователя;
- менять исходный текст без явного `MystemTextPreprocessor`;
- поддерживать XML/text output в первом MVP.

## Артефакт и namespace

Artifact:

```text
mystem4j-model
```

Package:

```text
io.github.ulviar.mystem4j.model
```

## JSON parser

Основной API:

```java
MystemDocument parse(MystemRawResult result);

MystemDocument parse(String originalText, String json);
```

Требования:

- `parse(MystemRawResult)` принимает только `MystemOutputFormat.JSON`.
- JSON root должен быть массивом MyStem items.
- Каждый item должен создавать `MystemToken`.
- Поле `text` становится `MystemToken.text`.
- Поле `analysis` становится списком `MystemAnalysis`; отсутствующее поле означает пустой список.
- Поле `lex` становится lemma.
- Поле `gr` парсится через `MystemGrammarParser`.
- Поле `wt`, если оно числовое, становится `OptionalDouble`.
- Неизвестные JSON-поля игнорируются.
- Некорректный JSON должен давать `MystemJsonParseException`.

Для MVP используется Jackson, а не ручной JSON parser.

## Offset alignment

Parser должен выравнивать каждый `MystemToken.text` с `originalText` слева направо:

1. Поиск начинается с cursor текущего документа.
2. При точном совпадении token получает `startOffset` и `endOffset`.
3. Cursor сдвигается на конец совпадения.
4. Если совпадение не найдено, offsets равны `-1`, а `MystemDocument.issues` содержит `UNMATCHED_TOKEN`.

Offsets задаются в Java UTF-16 code units. Это сознательное решение, потому что Lucene token offsets используют ту же модель индексации Java-строк.

## Grammar model

Типы:

- `MystemGrammar`
- `MystemGrammarVariant`
- `MystemAnalysis`

Правила:

- В строке `S,жен,од=им,ед` первое значение слева от `=` считается part of speech.
- Остальные значения слева от `=` считаются common grammemes.
- Правая часть разделяется по `|` на варианты.
- Каждый вариант разделяется по `,` на grammemes.
- Пустая правая часть, например `PR=`, допустима и означает один пустой вариант.

Компонент не нормализует граммемы и не переводит их между русскими/английскими именами. Это зависит от опции MyStem `--eng-gr`.

## Unicode preparation

`MystemTextPreprocessor.prepare(String)` должен возвращать `MystemPreparedText`.

Требования:

- валидные supplementary code points сохраняются;
- unpaired surrogate заменяется на `U+FFFD`;
- небезопасные control characters заменяются на пробел;
- `\n`, `\r`, `\t` сохраняются;
- каждое изменение фиксируется в `issues`;
- mapping должен позволять перевести offset подготовленного текста обратно к offset оригинального текста.

Preprocessor является opt-in. Parser не должен автоматически менять входной текст.

## Issue model

`MystemTextIssueType` MVP:

- `UNMATCHED_TOKEN`
- `UNPAIRED_SURROGATE`
- `CONTROL_CHARACTER`

Issue является диагностикой, а не исключением. Исключения используются только для структурно некорректного JSON или некорректного API-вызова.

## Acceptance criteria

- Есть модуль `mystem4j-model`.
- Parser строит `MystemDocument` из `MystemRawResult` и raw JSON.
- Parser извлекает lemma, grammar, weight и token text.
- Grammar parser покрывает common grammemes, variants и пустую правую часть.
- Offset alignment покрывает повторяющиеся токены и пропуски пунктуации.
- Невыровненный token дает issue и offsets `-1`.
- Unicode preprocessor заменяет unpaired surrogate и unsafe control characters.
- Все offsets документированы как Java UTF-16 offsets.
- Модуль не зависит от Lucene.
