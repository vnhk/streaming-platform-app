package com.bervan.streamingapp.view.player;

import com.bervan.common.component.BervanButton;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;

import java.util.Optional;

/**
 * Component for controlling subtitle delays
 */
public class SubtitleControlPanel extends VerticalLayout {
    private static final double SUBTITLE_DELAY_STEP = 0.5;
    private final NumberField enDelayField;
    private final NumberField esDelayField;
    private final NumberField plDelayField;

    public SubtitleControlPanel(double initialEnDelay, double initialPlDelay, double initialEsDelay) {
        setAlignItems(Alignment.CENTER);
        setSpacing(true);

        enDelayField = createDelayField("Subtitle Delay (EN) [s]", initialEnDelay);
        plDelayField = createDelayField("Subtitle Delay (PL) [s]", initialPlDelay);
        esDelayField = createDelayField("Subtitle Delay (ES) [s]", initialEsDelay);

        Button resetButton = new BervanButton(
                "Reset Delays",
                e -> resetDelays(initialEnDelay, initialPlDelay, initialEsDelay)
        );

        add(enDelayField, plDelayField, resetButton);
    }

    private NumberField createDelayField(String label, double initialValue) {
        NumberField field = new NumberField(label);
        field.setStep(SUBTITLE_DELAY_STEP);
        field.setWidth("300px");
        field.setValue(initialValue);
        return field;
    }

    private void resetDelays(double enDelay, double plDelay, double esDelay) {
        enDelayField.setValue(enDelay);
        plDelayField.setValue(plDelay);
        esDelayField.setValue(esDelay);
    }

    public void setEnDelayChangeListener(java.util.function.Consumer<Double> listener) {
        enDelayField.addValueChangeListener(e -> {
            double value = Optional.ofNullable(e.getValue()).orElse(0.0);
            listener.accept(value);
        });
    }

    public void setPlDelayChangeListener(java.util.function.Consumer<Double> listener) {
        plDelayField.addValueChangeListener(e -> {
            double value = Optional.ofNullable(e.getValue()).orElse(0.0);
            listener.accept(value);
        });
    }

    public void setEsDelayChangeListener(java.util.function.Consumer<Double> listener) {
        esDelayField.addValueChangeListener(e -> {
            double value = Optional.ofNullable(e.getValue()).orElse(0.0);
            listener.accept(value);
        });
    }

    public double getEnDelay() {
        return Optional.ofNullable(enDelayField.getValue()).orElse(0.0);
    }

    public double getPlDelay() {
        return Optional.ofNullable(plDelayField.getValue()).orElse(0.0);
    }

    public double getEsDelay() {
        return Optional.ofNullable(esDelayField.getValue()).orElse(0.0);
    }
}
