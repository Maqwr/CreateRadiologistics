package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/**
 * "Text Stream" node — packages TTS text, volume and pitch into an audio stream descriptor
 * that an Audio Play node can consume. Does NOT speak or send packets itself.
 */
public class TextSpeakNode extends AlgoNode {

    public TextSpeakNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "text_speak";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("text", "volume", "pitch");
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("stream");
    }

    @Override
    public CompoundTag saveProperties() {
        return new CompoundTag();
    }

    @Override
    public void loadProperties(CompoundTag tag) {
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (!"stream".equalsIgnoreCase(outputPort)) return null;

        Object textVal = inputValues.get("text");
        String text = textVal != null ? String.valueOf(textVal).trim() : "";

        Object volumeVal = inputValues.get("volume");
        double volume = 1.0;
        if (volumeVal instanceof Number num) {
            volume = num.doubleValue();
        } else if (volumeVal != null) {
            try { volume = Double.parseDouble(String.valueOf(volumeVal).trim()); } catch (NumberFormatException ignored) {}
        }
        volume = Math.max(0.0, Math.min(10.0, volume));

        Object pitchVal = inputValues.get("pitch");
        double pitch = 1.0;
        if (pitchVal instanceof Number num) {
            pitch = num.doubleValue();
        } else if (pitchVal != null) {
            try { pitch = Double.parseDouble(String.valueOf(pitchVal).trim()); } catch (NumberFormatException ignored) {}
        }

        if (text.isEmpty()) return "";

        String language = detectLanguage(text);

        return String.format(java.util.Locale.ROOT,
            "{\"audio_type\": \"tts\", \"text\": \"%s\", \"volume\": %.2f, \"pitch\": %.2f, \"language\": \"%s\"}",
            text.replace("\\", "\\\\").replace("\"", "\\\""), volume, pitch, language);
    }

    private String detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "en";
        }
        
        int uk = 0;
        int ru = 0;
        int pl = 0;
        int de = 0;
        int fr = 0;
        int es = 0;
        int it = 0;
        int ja = 0;
        int zh = 0;
        int en = 0;

        String lowerText = text.toLowerCase(java.util.Locale.ROOT);

        // 1. Character-level scoring
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            
            // Cyrillic auto-detection
            if (block == Character.UnicodeBlock.CYRILLIC) {
                // Ukrainian specific
                if (c == 'і' || c == 'ї' || c == 'є' || c == 'ґ' || c == 'І' || c == 'Ї' || c == 'Є' || c == 'Ґ') {
                    uk += 10;
                }
                // Russian specific
                else if (c == 'ы' || c == 'э' || c == 'ъ' || c == 'ё' || c == 'Ы' || c == 'Э' || c == 'Ъ' || c == 'Ё') {
                    ru += 10;
                }
                else {
                    uk += 1;
                    ru += 1;
                }
            }
            // Japanese (Hiragana/Katakana)
            else if (block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA) {
                ja += 5;
            }
            // CJK (Chinese / Japanese Kanji)
            else if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                zh += 2;
                ja += 1;
            }
            // Polish specific characters
            else if (c == 'ą' || c == 'ć' || c == 'ę' || c == 'ł' || c == 'ń' || c == 'ó' || c == 'ś' || c == 'ź' || c == 'ż' ||
                     c == 'Ą' || c == 'Ć' || c == 'Ę' || c == 'Ł' || c == 'Ń' || c == 'Ó' || c == 'Ś' || c == 'Ź' || c == 'Ż') {
                pl += 5;
            }
            // German eszett
            else if (c == 'ß' || c == 'ẞ') {
                de += 5;
            }
        }

        // 2. Word-level scoring
        String[] words = lowerText.split("[\\s\\p{Punct}]+");
        for (String w : words) {
            if (w.isEmpty()) continue;
            
            // English
            if (w.equals("the") || w.equals("and") || w.equals("that") || w.equals("this") || 
                w.equals("with") || w.equals("have") || w.equals("you") || w.equals("not") || 
                w.equals("but") || w.equals("are") || w.equals("for") || w.equals("was")) {
                en += 5;
            }
            // Ukrainian
            if (w.equals("і") || w.equals("й") || w.equals("та") || w.equals("що") || 
                w.equals("як") || w.equals("це") || w.equals("не") || w.equals("на") || 
                w.equals("для") || w.equals("був") || w.equals("була") || w.equals("було")) {
                uk += 5;
            }
            // Russian
            if (w.equals("и") || w.equals("что") || w.equals("как") || w.equals("это") || 
                w.equals("не") || w.equals("на") || w.equals("для") || w.equals("был") || 
                w.equals("была") || w.equals("было")) {
                ru += 5;
            }
            // German
            if (w.equals("der") || w.equals("die") || w.equals("das") || w.equals("und") || 
                w.equals("ist") || w.equals("ein") || w.equals("eine") || w.equals("nicht") || 
                w.equals("mit") || w.equals("von") || w.equals("zu") || w.equals("es") || w.equals("wie")) {
                de += 5;
            }
            // French
            if (w.equals("le") || w.equals("la") || w.equals("les") || w.equals("et") || 
                w.equals("est") || w.equals("un") || w.equals("une") || w.equals("dans") || 
                w.equals("pour") || w.equals("qui") || w.equals("que") || w.equals("avec")) {
                fr += 5;
            }
            // Spanish
            if (w.equals("el") || w.equals("los") || w.equals("las") || w.equals("un") || 
                w.equals("una") || w.equals("en") || w.equals("para") || w.equals("con") || 
                w.equals("por") || w.equals("que")) {
                es += 5;
            }
            // Italian
            if (w.equals("il") || w.equals("i") || w.equals("gli") || w.equals("le") || 
                w.equals("un") || w.equals("una") || w.equals("in") || w.equals("per") || 
                w.equals("con") || w.equals("che") || w.equals("non") || w.equals("sono")) {
                it += 5;
            }
            // Polish
            if (w.equals("w") || w.equals("na") || w.equals("z") || w.equals("do") || 
                w.equals("jest") || w.equals("że") || w.equals("się") || w.equals("nie")) {
                pl += 5;
            }
        }

        // Find the language with the highest score (if above 0)
        String bestLang = "en";
        int maxScore = 0;
        
        if (uk > maxScore) { maxScore = uk; bestLang = "uk"; }
        if (ru > maxScore) { maxScore = ru; bestLang = "ru"; }
        if (pl > maxScore) { maxScore = pl; bestLang = "pl"; }
        if (de > maxScore) { maxScore = de; bestLang = "de"; }
        if (fr > maxScore) { maxScore = fr; bestLang = "fr"; }
        if (es > maxScore) { maxScore = es; bestLang = "es"; }
        if (it > maxScore) { maxScore = it; bestLang = "it"; }
        if (ja > maxScore) { maxScore = ja; bestLang = "ja"; }
        if (zh > maxScore) { maxScore = zh; bestLang = "zh"; }
        
        return bestLang;
    }
}
