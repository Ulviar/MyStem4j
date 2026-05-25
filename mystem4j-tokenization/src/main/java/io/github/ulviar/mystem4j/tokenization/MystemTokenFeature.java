package io.github.ulviar.mystem4j.tokenization;

enum MystemTokenFeature {
    WORD,
    RUSSIAN_LEMMATIZED_WORD,
    NUMBER,
    ENDS_WITH_NUMBER_SIGN,
    ENDS_WITH_PLUS,
    ENDS_WITH_DOUBLE_PLUSES,
    SEPARATOR,
    URL_PART,
    EMAIL_PART,
    URL,
    EMAIL,
    CURRENCY
}
