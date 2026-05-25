package io.github.ulviar.mystem4j.tokenization;

import java.util.List;
import java.util.Map;

final class CurrencyData {
    private static final Map<String, List<String>> FORMS = Map.of(
            "$", List.of("доллар", "dollar", "dólar", "美元", "USD"),
            "€", List.of("евро", "euro", "欧元", "EUR"),
            "£", List.of("фунт", "pound", "pfund", "libra", "livre", "英镑", "GBP"),
            "₽", List.of("рубль", "ruble", "rubel", "rublo", "rouble", "卢布", "RUB"),
            "₣", List.of("франк", "franc", "franken", "franco", "法郎", "CHF"),
            "₩", List.of("вон", "won", "韩元", "KRW"),
            "₹", List.of("рупия", "rupee", "rupie", "rupia", "roupie", "卢比", "INR"),
            "¥", List.of("иена", "юань", "yen", "yuan", "日元", "元", "JPY"),
            "元", List.of("юань", "yuan", "CNY"));

    private CurrencyData() {}

    static List<String> forms(String symbol) {
        return FORMS.getOrDefault(symbol, List.of());
    }
}
