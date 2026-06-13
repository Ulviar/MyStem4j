# Спецификация компонента постобработки MyStem

Статус: baseline 0.1 для второго компонента
Дата: 2026-05-23

## Назначение

`mystem4j-model` отвечает за преобразование сырого вывода MyStem в типизированные структуры данных и за подготовку текста к безопасной работе с MyStem без потери offset-информации, нужной Lucene-слою.

Компонент находится между `mystem4j-runtime` и `mystem4j-lucene`.

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
MystemDocument parse(String originalText, String json);

MystemDocument parse(MystemPreparedText preparedText, String json);
```

Требования:

- JSON output должен содержать один или несколько top-level массивов MyStem items. Несколько массивов допустимы для multiline input в one-shot/file режимах MyStem.
- Каждый item должен создавать `MystemToken`.
- Поле `text` становится `MystemToken.text`.
- Поле `analysis` становится списком `MystemAnalysis`; отсутствующее поле означает пустой список.
- Поле `lex` становится lemma.
- Поле `gr` парсится через `MystemGrammarParser`.
- Поле `wt`, если оно числовое, становится `OptionalDouble`.
- Неизвестные JSON-поля игнорируются.
- Некорректный JSON должен давать `MystemJsonParseException`.

Для MVP используется Jackson Core Streaming API, а не ручной JSON parser или databind/tree model.

## Offset alignment

Parser должен выравнивать каждый `MystemToken.text` слева направо:

1. Поиск начинается с cursor текущего документа.
2. При точном совпадении token получает `startOffset` и `endOffset`.
3. Cursor сдвигается на конец совпадения.
4. Если совпадение не найдено, offsets равны `-1`, а `MystemDocument.issues` содержит `UNMATCHED_TOKEN`.

Если точное совпадение не найдено, parser может использовать ограниченное fuzzy alignment для символов, которые MyStem удаляет из surface text. В baseline поддерживается `U+00AD SOFT HYPHEN`: token `Один` должен выравниваться с исходным `О\u00ADд\u00ADи\u00ADн` и возвращать range всего исходного слова.

`parse(String originalText, String json)` выравнивает token text прямо с `originalText`.

`parse(MystemPreparedText preparedText, String json)` выравнивает token text с `preparedText.text()`, но возвращает offsets в координатах `preparedText.originalText()`. Issues preprocessor должны попадать в `MystemDocument.issues` вместе с issues alignment.

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
- Unicode noncharacters заменяются на пробел;
- `\n`, `\r`, `\t` сохраняются;
- каждое изменение фиксируется в `issues`;
- mapping должен позволять перевести offset подготовленного текста обратно к offset оригинального текста.

Preprocessor является opt-in. Parser не должен автоматически менять входной текст.

## Issue model

`MystemTextIssueType` MVP:

- `UNMATCHED_TOKEN`
- `UNPAIRED_SURROGATE`
- `CONTROL_CHARACTER`
- `NONCHARACTER`

Issue является диагностикой, а не исключением. Исключения используются только для структурно некорректного JSON или некорректного API-вызова.

## Unicode verification

Обычный test suite должен содержать быстрые exhaustive checks без MyStem:

- `MystemOffsetAligner` возвращает UTF-16 offsets для каждого Unicode scalar value;
- `MystemTextPreprocessor` имеет total, monotonic mapping для всех UTF-16 code units;
- parser корректно мапит offsets из prepared text обратно в original text.
- parser принимает сгенерированный MyStem-like JSON с Unicode token text, lemma,
  grammar и weight, сохраняя offsets и порядок;
- malformed JSON-like input завершается `MystemJsonParseException`, а не
  произвольными runtime errors;
- grammar parser устойчив к случайным строкам с `,`, `=`, `|`, `(`, `)` и
  Unicode atoms.
- Jazzer/JUnit regression-mode harness проверяет те же parser и grammar
  invariants на seed corpus при обычном `test`; active fuzzing включается
  отдельно через `JAZZER_FUZZ=1`.

Real-MyStem tests должны быть opt-in через `mystem4j.executable`, потому что они зависят от native binary:

- representative Unicode corpus запускается с реальным MyStem и проверяет отсутствие `UNMATCHED_TOKEN`;
- полный all-scalar стресс запускается только с `-Dmystem4j.unicodeStress=true` и проверяет фактическое поведение MyStem на всем диапазоне Unicode scalar values.

Пример полного stress gate:

```bash
./gradlew :mystem4j-model:test \
  --tests io.github.ulviar.mystem4j.model.RealMystemUnicodeOffsetIntegrationTest.stressChecksAllUnicodeScalarValuesWithRealMystem \
  -Dmystem4j.executable=/path/to/mystem \
  -Dmystem4j.unicodeStress=true
```

## Acceptance criteria

- Есть модуль `mystem4j-model`.
- Parser строит `MystemDocument` из original text и raw JSON.
- Parser извлекает lemma, grammar, weight и token text.
- Grammar parser покрывает common grammemes, variants и пустую правую часть.
- Offset alignment покрывает повторяющиеся токены и пропуски пунктуации.
- Невыровненный token дает issue и offsets `-1`.
- Unicode preprocessor заменяет unpaired surrogate, unsafe control characters и Unicode noncharacters.
- Все offsets документированы как Java UTF-16 offsets.
- Модуль не зависит от Lucene.
