package com.alechilles.alecsconsole;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** Data sent by Alec's Console UI controls. */
public final class ConsoleEventData {
    static final BuilderCodec<ConsoleEventData> CODEC = BuilderCodec
            .builder(ConsoleEventData.class, ConsoleEventData::new)
            .addField(new KeyedCodec<>("Action", BuilderCodec.STRING),
                    (data, value) -> data.action = value, data -> data.action)
            .addField(new KeyedCodec<>("@Value", BuilderCodec.STRING),
                    (data, value) -> data.value = value, data -> data.value)
            .addField(new KeyedCodec<>("RowSequence", BuilderCodec.STRING),
                    (data, value) -> data.rowSequence = value, data -> data.rowSequence)
            .addField(new KeyedCodec<>("@CatalogId", BuilderCodec.STRING),
                    (data, value) -> data.catalogId = value, data -> data.catalogId)
            .build();

    enum Action {
        SourceChanged,
        FileChanged,
        LevelChanged,
        LoggerChanged,
        SearchTextChanged,
        SearchSubmitted,
        PauseToggled,
        MultiSelectToggled,
        RowSelected,
        ClearSelection,
        PreviousPage,
        NextPage,
        RefreshFiles;

        static Action parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    private String action;
    private String value;
    private String rowSequence;
    private String catalogId;

    private ConsoleEventData() {
    }

    Action action() {
        return Action.parse(action);
    }

    String value() {
        return value;
    }

    String catalogId() {
        return catalogId;
    }

    Long parsedRowSequence() {
        if (rowSequence == null) {
            return null;
        }
        try {
            return Long.parseLong(rowSequence);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
