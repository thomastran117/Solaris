package backend.configurations.search;

import co.elastic.clients.elasticsearch.indices.IndexSettings;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchIndexSettings {

    public IndexSettings buildSettings() {
        return IndexSettings.of(s -> s
                .analysis(a -> a
                        .filter("synonym_filter", f -> f
                                .synonymGraph(sg -> sg
                                        .synonyms(List.of(
                                                "laptop, notebook, computer",
                                                "shoes, sneakers, trainers",
                                                "tv, television, screen",
                                                "phone, smartphone, mobile",
                                                "headphones, earphones, earbuds",
                                                "jacket, coat, outerwear",
                                                "sofa, couch, settee",
                                                "fridge, refrigerator",
                                                "tshirt, t-shirt, tee",
                                                "pants, trousers, jeans",
                                                "watch, timepiece",
                                                "bag, handbag, purse, tote",
                                                "camera, dslr, mirrorless",
                                                "desk, table, workstation",
                                                "chair, seat, stool",
                                                "bicycle, bike, cycle",
                                                "running, jogging",
                                                "gaming, games, gamer",
                                                "wireless, bluetooth",
                                                "organic, natural",
                                                "kids, children, child, toddler",
                                                "sale, discount, clearance, deals"
                                        ))
                                )
                        )
                        .filter("autocomplete_filter", f -> f
                                .edgeNgram(en -> en
                                        .minGram(1)
                                        .maxGram(20)
                                )
                        )
                        .analyzer("product_search", an -> an
                                .custom(c -> c
                                        .tokenizer("standard")
                                        .filter(List.of("lowercase", "synonym_filter"))
                                )
                        )
                        .analyzer("autocomplete_index", an -> an
                                .custom(c -> c
                                        .tokenizer("standard")
                                        .filter(List.of("lowercase", "autocomplete_filter"))
                                )
                        )
                        .analyzer("autocomplete_search", an -> an
                                .custom(c -> c
                                        .tokenizer("standard")
                                        .filter(List.of("lowercase"))
                                )
                        )
                )
        );
    }
}
