# Спецификация компонента взаимодействия с MyStem

Статус: baseline 0.3 для первой реализации
Дата: 2026-05-23

## Назначение

Компонент взаимодействия с MyStem отвечает за надежный запуск внешней CLI-программы MyStem, передачу текста на анализ и возврат сырого результата вызывающему коду.

Этот компонент является нижним слоем будущей библиотеки MyStem4j. Он не должен зависеть от Lucene и не должен выполнять полноценную морфологическую интерпретацию результата. Его задача - предоставить управляемый, тестируемый и безопасный runtime поверх MyStem CLI.

## Источники требований

Официальная документация MyStem фиксирует следующие свойства, важные для runtime-слоя:

- MyStem является консольной программой для морфологического анализа русского текста.
- Запуск выполняется в форме `mystem [опции] [входной файл] [выходной файл]`.
- При отсутствии файлов или при указании `-` используются стандартный ввод и стандартный вывод.
- Поддерживаются форматы вывода `text`, `xml`, `json`.
- По умолчанию используется кодировка `utf-8`.
- Актуальная опубликованная версия на странице загрузки на 2026-05-22 - 3.1.

Ссылки:

- https://yandex.ru/dev/mystem/
- https://yandex.ru/dev/mystem/doc/ru/
- https://yandex.ru/legal/mystem/ru/

## Место в архитектуре

Целевая архитектура MyStem4j состоит из нескольких слоев:

1. `mystem4j-runtime` - взаимодействие с MyStem CLI.
2. `mystem4j-kotlin` - Kotlin-friendly API поверх runtime.
3. `mystem4j-gradle-plugin` - подготовка MyStem binary для dev/test/build/distribution сценариев.
4. `mystem4j-model` - парсинг, нормализация и Unicode-aware представление результата.
5. `mystem4j-lucene` - Lucene `Analyzer`, `Tokenizer`, `TokenFilter` и фабрики.

Данная спецификация описывает первый milestone: `mystem4j-runtime`, Kotlin API для него и Gradle-плагин подготовки бинаря MyStem. Слои `model` и `lucene` описываются отдельно.

## Граница ответственности

`mystem4j-runtime` должен:

- находить и запускать бинарь MyStem;
- рендерить типизированные настройки MyStem в CLI-аргументы;
- поддерживать одноразовый запуск, переиспользуемую сессию и пул процессов;
- передавать запросы в stdin или через временные файлы;
- передавать входной и выходной файлы напрямую в MyStem CLI;
- читать stdout и stderr;
- возвращать сырой результат в выбранном формате;
- применять лимиты, таймауты и корректное завершение процессов;
- предоставлять диагностическую информацию и предсказуемую модель ошибок.

`mystem4j-runtime` не должен:

- поставлять бинарь MyStem внутри jar по умолчанию;
- обходить или скрывать лицензионные ограничения MyStem;
- зависеть от Lucene;
- выполнять токенизацию для Lucene;
- нормализовать Unicode для поискового пайплайна;
- выбирать основную лемму или интерпретировать граммемы;
- предоставлять async API в первом релизе.

`mystem4j-gradle-plugin` должен отвечать за скачивание, кэширование и подготовку бинаря MyStem. Runtime-библиотека не должна сама ходить в сеть.

## Namespace и артефакты

Базовый Java package:

```text
io.github.ulviar.mystem4j
```

Артефакты первого milestone:

- `mystem4j-runtime`
- `mystem4j-kotlin`
- `mystem4j-gradle-plugin`

## Базовая зависимость

Runtime должен строиться поверх iCLI.

Минимально полезные возможности iCLI для этого слоя:

- `Icli.command(...)` как единая точка входа;
- `run()` для одноразовых запусков;
- `protocolSession(...)` для долгоживущей интерактивной сессии;
- `protocolSession(...).pooled()` для пула процессов;
- таймауты, лимиты stdout/stderr, transcript, health-check/reset hooks;
- корректное закрытие и перезапуск воркеров.

Базовая версия зависимости для первого milestone: `com.github.ulviar:icli:0.1.0`.

## Публичный API

### Основные типы

```java
public interface MystemClient extends AutoCloseable {
    MystemRawResult analyze(String text);

    MystemFileContentResult analyzeFile(Path input);

    MystemFileResult analyzeFile(Path input, Path output);

    default List<MystemRawResult> analyzeAll(Collection<String> texts) {
        return texts.stream().map(this::analyze).toList();
    }

    @Override
    void close();
}
```

```java
public record MystemRawResult(
        String input,
        String output,
        MystemOutputFormat format,
        MystemRequestStats stats
) {
}
```

```java
public record MystemFileContentResult(
        Path input,
        String output,
        MystemOutputFormat format,
        MystemRequestStats stats
) {
}
```

```java
public record MystemFileResult(
        Path input,
        Path output,
        MystemOutputFormat format,
        MystemRequestStats stats
) {
}
```

```java
public enum MystemOutputFormat {
    TEXT,
    XML,
    JSON
}
```

```java
public final class Mystem {
    public static MystemClientBuilder builder();
}
```

### Builder

```java
MystemClient client = Mystem.builder()
        .executable(Path.of("/usr/local/bin/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .englishGrammemes(true)
                .build())
        .pooled(pool -> pool
                .maxSize(4)
                .warmupSize(1)
                .acquireTimeout(Duration.ofSeconds(2)))
        .requestTimeout(Duration.ofSeconds(3))
        .build();
```

Требования:

- Builder должен быть immutable-style: каждый `with...`/setter либо возвращает новый конфиг, либо builder до `build()` не шарится между потоками.
- `MystemClient`, созданный в pooled-режиме, должен быть thread-safe.
- `MystemClient`, созданный в reusable session-режиме, может быть не thread-safe, если это явно указано в Javadoc.
- Все клиенты должны реализовывать `AutoCloseable`.

### Файловый API

Файловый API является обязательной частью первого milestone.

Требования:

- `analyzeFile(Path input)` запускает MyStem в файловом режиме и возвращает результат как строку в `MystemFileContentResult`.
- `analyzeFile(Path input, Path output)` запускает MyStem в файловом режиме и пишет результат в указанный файл без обязательного чтения всего результата в память.
- Файловый режим должен использовать штатную форму запуска MyStem: `mystem [опции] [входной файл] [выходной файл]`.
- Файловый режим является one-shot режимом: каждый вызов запускает отдельный процесс.
- Для больших корпусов предпочтителен `analyzeFile(input, output)`, потому что он не требует буферизовать весь stdout в JVM.
- Валидация должна проверять существование и доступность чтения входного файла, а также возможность создать или перезаписать выходной файл.
- Runtime не должен удалять пользовательский output-файл при ошибке, если файл уже был создан MyStem. Поведение должно быть задокументировано.

### Kotlin API

Kotlin-обвязка является обязательной частью первого milestone. Java API должен оставаться удобным из Kotlin без дополнительных адаптеров, а модуль `mystem4j-kotlin` должен добавить тонкий DSL и idiomatic helpers.

```kotlin
Mystem.builder()
    .executable(Path("/usr/local/bin/mystem"))
    .options(
        MystemOptions.builder()
            .format(MystemOutputFormat.JSON)
            .grammarInfo(true)
            .disambiguate(true)
            .build()
    )
    .pooled { it.maxSize(4) }
    .build()
    .use { client ->
        val result = client.analyze("Мама мыла раму.")
    }
```

Минимум для `mystem4j-kotlin`:

- DSL для builder API;
- extension functions для `Path`, `String` и `use`;
- Kotlin examples в README;
- без coroutine API в первом релизе, потому что runtime и Lucene-пайплайны остаются синхронными.

## Конфигурация MyStem

Runtime должен предоставить типизированный `MystemOptions`, покрывающий штатные CLI-опции MyStem.

| API-поле | CLI | Описание |
| --- | --- | --- |
| `newLineEachWord` | `-n` | Печатать каждое слово на новой строке. |
| `copyInput` | `-c` | Копировать весь ввод на вывод, включая межсловные промежутки. |
| `dictionaryWordsOnly` | `-w` | Печатать только словарные слова. |
| `lemmaOnly` | `-l` | Не печатать исходные словоформы. |
| `grammarInfo` | `-i` | Печатать грамматическую информацию. |
| `mergeWordForms` | `-g` | Склеивать информацию словоформ при одной лемме. |
| `sentenceMarkers` | `-s` | Печатать маркер конца предложения. |
| `encoding` | `-e` | Кодировка ввода/вывода. |
| `disambiguate` | `-d` | Применить контекстное снятие омонимии. |
| `englishGrammemes` | `--eng-gr` | Печатать английские обозначения граммем. |
| `filterGrammar` | `--filter-gram` | Строить разборы только с указанными граммемами. |
| `fixlist` | `--fixlist` | Использовать файл пользовательского словаря. |
| `format` | `--format` | `text`, `xml` или `json`. |
| `generateAll` | `--generate-all` | Генерировать все гипотезы для несловарных слов. |
| `weight` | `--weight` | Печатать бесконтекстную вероятность леммы. |

Валидация:

- `mergeWordForms=true` допустим только при `grammarInfo=true`.
- `sentenceMarkers=true` допустим только при `copyInput=true`.
- `encoding` должен принимать только значения, поддержанные MyStem: `cp866`, `cp1251`, `koi8-r`, `utf-8`.
- `format` по умолчанию должен быть `JSON`, потому что это основной машинно-читаемый режим для следующих слоев.
- `fixlist` должен проверяться на существование и доступность чтения на этапе `build()` или первого запуска.
- Набор CLI-аргументов должен быть детерминированным: одинаковый `MystemOptions` всегда дает одинаковый список аргументов.

## Runtime-режимы

### One-shot

Каждый вызов `analyze` запускает отдельный процесс MyStem, передает один запрос, читает полный вывод и завершает процесс.

Требования:

- Поддерживает все форматы MyStem: `text`, `xml`, `json`.
- Подходит для тестов, CLI-утилит и малых объемов.
- Должен корректно обрабатывать exit code, stderr и таймаут процесса.
- Может использовать stdin/stdout или временные файлы, если это явно указано в конфигурации.

### Reusable session

Один долгоживущий процесс MyStem обслуживает последовательные запросы.

Требования:

- Режим должен быть закрываемым через `close()`.
- Одновременно должен выполняться только один запрос на одну сессию.
- По умолчанию reusable session поддерживается только для протоколов, где runtime умеет надежно определить границу ответа.
- Для MVP встроенный надежный reusable-протокол должен быть JSON line protocol: один входной текст завершается `\n`, один ответ MyStem читается как одна JSON-строка.
- Для `text` и `xml` reusable-режим из коробки не поддерживается. Эти форматы доступны через one-shot и файловый режим.

### Pooled session

Пул долгоживущих процессов MyStem обслуживает конкурентные запросы.

Требования:

- `MystemClient` в pooled-режиме должен быть thread-safe.
- Пул должен иметь настройки `maxSize`, `minIdle`, `warmupSize`, `acquireTimeout`, `maxRequestsPerWorker`, `maxWorkerAge`.
- Нездоровый или упавший worker должен удаляться из пула и заменяться новым, если клиент не закрыт.
- В pooled-режиме нельзя менять `MystemOptions` на уровне отдельного запроса. Опции являются свойством процесса.
- При `close()` пул должен перестать принимать новые запросы, дождаться активных в рамках заданного таймаута и завершить процессы.

## Протокол запрос/ответ

### Общие правила

- Входной API принимает `String`.
- Runtime кодирует строку в кодировку, указанную в `MystemOptions.encoding`.
- По умолчанию используется UTF-8.
- Runtime не должен выполнять Unicode-нормализацию, исправление суррогатных пар, фильтрацию управляющих символов или token boundary analysis. Это ответственность следующего слоя.
- Runtime должен считать и ограничивать и символы, и байты, потому что Lucene-слой будет работать с Unicode, а процесс - с байтовыми потоками.

### JSON line protocol

MVP reusable/pool режим должен использовать JSON line protocol:

- MyStem запускается с `--format json`.
- Один запрос записывается в stdin как одна строка.
- Внутренние `\r` и `\n` в пользовательском тексте отклоняются, потому что первый milestone поддерживает только один входной line frame.
- Один ответ читается как один JSON-документ до `\n`. Это зафиксированное свойство для MyStem 3.1; оно подтверждено smoke-проверкой на macOS и должно быть покрыто real MyStem integration test.
- Runtime возвращает этот JSON как сырой `String` без семантического парсинга.

Стратегия обработки многострочного текста в pooled/reusable режиме: `REJECT_MULTILINE`.

Причина: первый компонент должен быть протокольно надежным. Восстановление исходных переносов и Unicode-aware offset mapping относится ко второму компоненту.

### Custom protocol adapter

Custom protocol adapters не входят в публичный API первого milestone.

Причина: для целевого pooled/reusable сценария достаточно JSON line protocol, а дополнительные протоколы увеличивают поверхность API без прямой пользы для MyStem4j. Если позже появится реальный use case, расширение можно добавить отдельным advanced API без изменения базового клиента.

## Поиск бинаря MyStem

Способы задания executable, по убыванию приоритета:

1. Явный `Path` в builder.
2. System property `mystem4j.executable`.
3. Environment variable `MYSTEM_PATH`.
4. Путь, подготовленный `mystem4j-gradle-plugin` для test/runtime задач.
5. Поиск `mystem` в `PATH`, если включен `searchPath(true)`.

Требования:

- По умолчанию runtime не должен скачивать MyStem.
- Ошибка отсутствия бинаря должна быть отдельным типом исключения.
- Должен быть метод `MystemProbe.probe(...)`, который проверяет запуск и выполнение JSON smoke-запроса. Версия/баннер фиксируются позже, если MyStem предоставит стабильный CLI-способ получить версию.

## Gradle-плагин подготовки MyStem

Скачивание MyStem должно быть реализовано отдельным Gradle-плагином, а не runtime-библиотекой.

Предварительный plugin id:

```text
io.github.ulviar.mystem4j
```

Предварительный artifact:

```text
mystem4j-gradle-plugin
```

### Назначение

Плагин должен:

- выбирать дистрибутив MyStem под текущую ОС;
- скачивать архив MyStem из официального источника;
- кэшировать скачанный архив локально для проекта и не перекачивать его без изменения версии, URL или checksum;
- распаковывать бинарь в build directory проекта;
- выставлять путь к бинарю для `Test` задач при явном `configureTests=true`;
- запускать smoke probe после подготовки;
- поддерживать подготовку бинаря для сборки и поставки приложения.

Runtime при этом остается offline-friendly: он получает готовый `Path` и не знает, как бинарь был установлен.

### Поддерживаемые дистрибутивы

Для первого релиза поддерживается только актуальная опубликованная версия MyStem 3.1.

Поддерживаемые официальные архивы MyStem 3.1:

- `mystem-3.1-win-64bit.zip`
- `mystem-3.1-linux-64bit.tar.gz`
- `mystem-3.1-macosx.tar.gz`

MyStem 3.0 и более старые версии не поддерживаются.

### Пример конфигурации

```kotlin
plugins {
    id("io.github.ulviar.mystem4j") version "<version>"
}

mystem4j {
    version.set("3.1")
    acceptYandexMystemLicense.set(true)
    download.set(true)
    configureTests.set(true)
}
```

Требования:

- `version` по умолчанию равен актуальной версии, поддержанной библиотекой. Для первого релиза это `3.1`.
- `download` должен быть явным opt-in, чтобы сборка не ходила в сеть неожиданно.
- `configureTests` по умолчанию должен быть `false`; тестовые задачи подключаются к подготовленному MyStem только при явном opt-in.
- `acceptYandexMystemLicense` должен быть явным opt-in для задач скачивания.
- В non-interactive CI скачивание должно падать с понятной ошибкой, если лицензия не принята явно.
- Плагин должен позволять заменить URL дистрибутива на внутренний mirror.
- Плагин должен не перекачивать архив, если версия, ОС, URL и checksum совпадают с локальным metadata sidecar.
- Shared Gradle user-home cache является отдельным улучшением после первого milestone; текущая реализация допускает project-local cache в `build/mystem/downloads`.
- Если официальные checksums недоступны, плагин должен поддерживать пользовательский `sha256` и lock-файл, но не блокировать локальный dev-сценарий.

### Задачи

Минимальный набор задач:

- `mystemDownload` - скачать архив в project-local cache.
- `mystemExtract` - распаковать бинарь в `build/mystem/bin/<platform>/`.
- `mystemProbe` - проверить запуск бинаря и smoke-запрос.
- `mystemPrepareTestRuntime` - подготовить `mystem4j.executable` для тестов.
- `mystemPrepareDistribution` - подготовить бинарь для включения в distribution только при явном opt-in.

`mystemPrepareDistribution` не должен включать бинарь MyStem в публикацию библиотеки `mystem4j-runtime`. Эта задача нужна для приложений, которые осознанно поставляют MyStem вместе с собой и самостоятельно отвечают за лицензионную сторону.

## Таймауты и лимиты

Публичные настройки первого milestone:

- `requestTimeout`
- `idleTimeout` для reusable/pool
- `maxRequestChars`
- `maxRequestBytes`
- `maxResponseChars`
- `maxResponseBytes`
- `includeInputInDiagnostics`

Startup/shutdown/transcript/backlog детализация управляется через iCLI и может быть вынесена в публичный API позже, если появится реальный use case.

Значения по умолчанию должны быть консервативными и безопасными для серверного применения:

- `requestTimeout`: 3 секунды
- `maxRequestChars`: 1_000_000
- `maxRequestBytes`: 4_000_000
- `maxResponseChars`: 8_000_000
- `maxResponseBytes`: 32_000_000

Эти значения можно уточнить после первых benchmarks с реальным MyStem.

## Модель ошибок

Базовый тип:

```java
public class MystemException extends RuntimeException {
}
```

Специализированные ошибки:

- `MystemExecutableNotFoundException`
- `MystemInvalidOptionsException`
- `MystemStartupException`
- `MystemRequestTimeoutException`
- `MystemOutputLimitException`
- `MystemProtocolException`
- `MystemProcessException`
- `MystemPoolExhaustedException`
- `MystemClosedException`

Требования:

- Исключения должны содержать безопасный diagnostic message.
- Полный input не должен попадать в exception message по умолчанию.
- Для отладки допускается включаемый флаг `includeInputInDiagnostics`, по умолчанию `false`.
- Ошибки процесса должны включать exit code, если он известен, и усеченный stderr transcript.
- Interrupted state должен восстанавливаться при перехвате `InterruptedException`.

## Метрики и диагностика

`MystemRequestStats` должен включать:

- длительность запроса;
- режим выполнения: `ONE_SHOT_TEXT`, `ONE_SHOT_FILE`, `SESSION`, `POOL`;
- размер входа в chars/bytes;
- размер выхода в chars/bytes;
- идентификатор worker, если применимо;
- признак перезапуска worker, если применимо.

Listener API не входит в публичный API первого milestone. Возможное будущее расширение:

```java
public interface MystemRuntimeListener {
    void onProcessStarted(MystemProcessInfo process);

    void onProcessStopped(MystemProcessInfo process);

    void onRequestCompleted(MystemRequestStats stats);

    void onRequestFailed(MystemRequestStats stats, Throwable error);
}
```

Если listener будет добавлен позже, API должен не навязывать конкретный logging facade.

## Потокобезопасность

- `MystemOptions` и runtime-конфиги должны быть immutable.
- `MystemRawResult` должен быть immutable.
- `MystemClient` в one-shot режиме должен быть thread-safe.
- `MystemClient` в pooled режиме должен быть thread-safe.
- `MystemClient` в reusable session режиме должен быть либо не thread-safe с явной документацией, либо синхронизированным. Предпочтение: не thread-safe, потому что конкурентность должна решаться пулом.

## Лицензирование и распространение MyStem

Runtime-библиотека не должна включать бинарь MyStem в основной artifact.

Причины:

- MyStem распространяется по отдельному лицензионному соглашению Яндекса.
- Пользователь библиотеки должен сам принять условия использования MyStem.
- Runtime должен быть пригоден для окружений, где MyStem установлен отдельно системным пакетом, Docker image layer или внутренним artifact management.

Подготовка бинаря в первом milestone выполняется через `mystem4j-gradle-plugin`.

Плагин может:

- скачать бинарь с явным opt-in;
- подготовить dev/test окружение;
- подготовить бинарь для application distribution с явным opt-in.

Плагин не должен:

- включать бинарь MyStem в публикацию `mystem4j-runtime`;
- принимать лицензию автоматически;
- скрывать ссылку на лицензионные условия MyStem.

Допустимые будущие расширения:

- Maven plugin для подготовки dev/test окружения;
- Docker image example.

## Совместимость

Целевые версии:

- Единственная поддерживаемая линия для первого релиза: актуальная опубликованная версия MyStem 3.1.
- MyStem 3.0 и более старые версии не являются целевыми и не тестируются.

Java baseline:

- Java 17 для первого релиза.

Зависимости:

- Mandatory: iCLI.
- Mandatory for Kotlin artifact: Kotlin standard library.
- Optional: тестовые зависимости, fake CLI harness.
- Не добавлять Lucene в runtime-модуль.
- Не добавлять JSON parser как mandatory dependency, если runtime возвращает сырой JSON.

## Тестовая стратегия

### Unit tests

- Рендеринг всех `MystemOptions` в CLI-аргументы.
- Валидация несовместимых опций.
- Поиск executable по builder/property/env/PATH.
- Обработка многострочного ввода в JSON line protocol.
- Маппинг iCLI/runtime ошибок в `MystemException`.
- Валидация файлового API.
- Kotlin DSL и extension functions.

### Integration tests без настоящего MyStem

Нужен fake CLI, который:

- читает stdin;
- пишет stdout в формате, похожем на MyStem;
- умеет зависать для timeout-тестов;
- умеет падать с заданным exit code;
- пишет stderr;
- имитирует большие ответы.
- поддерживает файловый режим `input output`.

Эти тесты должны выполняться в CI всегда.

### Integration tests с настоящим MyStem

Включаются только при наличии `MYSTEM_PATH` или `-Dmystem4j.executable=...`.

Покрытие:

- smoke-запрос в one-shot режиме;
- smoke-запрос в JSON reusable режиме;
- pooled-запросы из нескольких потоков;
- проверка, что один запрос в JSON reusable режиме возвращает один JSON-документ одной строкой;
- файловый запуск `input output`;
- проверка `--format json`, `-i`, `-d`, `--eng-gr`, `--weight`.

### Gradle plugin tests

- выбор архива по ОС;
- формирование URL официального дистрибутива и mirror URL;
- кэширование скачанного архива;
- отказ скачивания без `acceptYandexMystemLicense=true`;
- настройка `Test` task через `mystem4j.executable`;
- `mystemPrepareDistribution` только при явном opt-in.

### Stress tests

Отдельная задача, не обязательная для обычного `check`:

- 10_000 коротких запросов через пул;
- конкурентность 2x, 4x, 8x от размера пула;
- принудительные restarts через `maxRequestsPerWorker`;
- проверка отсутствия зависших процессов после `close()`.

## Acceptance criteria для первого milestone

Первый компонент считается готовым, когда выполнены условия:

- Есть модуль `mystem4j-runtime`.
- Есть модуль `mystem4j-model`.
- Есть модуль `mystem4j-kotlin`.
- Есть модуль `mystem4j-gradle-plugin`.
- Можно создать one-shot `MystemClient` и получить сырой результат MyStem.
- Можно создать pooled `MystemClient` для `--format json` и выполнить конкурентные запросы.
- Можно выполнить файловый анализ `input -> output` без загрузки результата в память.
- Все штатные опции MyStem представлены типизированно.
- Ошибки запуска, timeout, превышение лимита и падение процесса имеют отдельные исключения.
- Клиенты корректно закрывают процессы.
- Kotlin DSL покрыт тестами и README-примерами.
- Gradle-плагин умеет скачать, закэшировать, распаковать и проверить MyStem 3.1 для поддерживаемой ОС.
- Unit tests и fake CLI integration tests проходят без установленного MyStem.
- Real MyStem integration tests включаются условно и документированы.
- В runtime-модуле нет зависимости от Lucene.
- В основной artifact не входит бинарь MyStem.

## Открытые решения

1. Нужен ли отдельный `MystemBatchClient` поверх файлового API или достаточно методов `analyzeFile(...)`.
2. Нужен ли встроенный список официальных checksums, если Яндекс начнет публиковать их стабильным образом.
3. Поддерживать ли cross-platform download для чужой ОС, например подготовку Linux-бинаря на macOS для Docker image.
4. Нужен ли shared Gradle user-home cache поверх текущего project-local cache.

## Рекомендуемый порядок реализации

1. Создать Gradle multi-module skeleton.
2. Зафиксировать package namespace `io.github.ulviar.mystem4j`.
3. Подключить iCLI как зависимость runtime-модуля.
4. Реализовать `MystemOptions` и тесты рендеринга аргументов.
5. Реализовать executable resolution и probe.
6. Реализовать one-shot string client.
7. Реализовать one-shot file client.
8. Реализовать JSON line reusable client.
9. Реализовать pooled client.
10. Добавить fake CLI integration tests.
11. Добавить `mystem4j-kotlin` DSL и Kotlin examples.
12. Добавить `mystem4j-gradle-plugin` с download/cache/extract/probe задачами.
13. Добавить optional real MyStem integration tests.
14. Зафиксировать README с минимальными Java/Kotlin/Gradle примерами.
