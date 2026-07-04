package com.troquim_bot.application.language;

import com.troquim_bot.application.language.vocabulary.BrazilianVocabulary;
import java.util.Objects;

public class LanguagePipeline {

    private final TextNormalizer textNormalizer;
    private final BusinessDictionary businessDictionary;
    private final BrazilianVocabulary brazilianVocabulary;

    public LanguagePipeline() {
        this(new TextNormalizer(), new BusinessDictionary(), new BrazilianVocabulary());
    }

    public LanguagePipeline(TextNormalizer textNormalizer, BusinessDictionary businessDictionary, BrazilianVocabulary brazilianVocabulary) {
        this.textNormalizer = Objects.requireNonNull(textNormalizer, "textNormalizer must not be null");
        this.businessDictionary = Objects.requireNonNull(businessDictionary, "businessDictionary must not be null");
        this.brazilianVocabulary = Objects.requireNonNull(brazilianVocabulary, "brazilianVocabulary must not be null");
    }

    public String process(String text) {
        String normalized = textNormalizer.normalize(text);
        String withVocabulary = brazilianVocabulary.normalize(normalized);
        return businessDictionary.canonicalize(withVocabulary);
    }

    public String normalize(String text) {
        String normalized = textNormalizer.normalize(text);
        return brazilianVocabulary.normalize(normalized);
    }
}
