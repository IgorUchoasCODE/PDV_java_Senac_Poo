package br.com.senac.application.terminal;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Capturador de áudio do microfone padrão (16kHz, 16-bit, mono WAV) com fallback
 * automático para placas de som Windows/Linux (44.1kHz / 48kHz / estéreo) e conversão para WAV 16kHz.
 */
public class CapturadorVozMicrofone {

    private static final AudioFormat FORMATO_ALVO_IA = new AudioFormat(16000.0f, 16, 1, true, false);
    private static final List<AudioFormat> FORMATOS_FALLBACK = List.of(
            FORMATO_ALVO_IA,
            new AudioFormat(44100.0f, 16, 1, true, false),
            new AudioFormat(44100.0f, 16, 2, true, false),
            new AudioFormat(48000.0f, 16, 1, true, false),
            new AudioFormat(48000.0f, 16, 2, true, false),
            new AudioFormat(22050.0f, 16, 1, true, false),
            new AudioFormat(8000.0f, 16, 1, true, false)
    );

    private TargetDataLine linhaMicrofone;
    private AudioFormat formatoCapturaAtivo = FORMATO_ALVO_IA;
    private ByteArrayOutputStream bufferPcm;
    private volatile boolean gravando = false;

    public boolean isMicrofoneDisponivel() {
        return localizarLinhaCompativel(false) != null;
    }

    public boolean isGravando() {
        return gravando;
    }

    public synchronized void iniciarGravacao() throws LineUnavailableException {
        if (gravando) {
            return;
        }
        TargetDataLine linha = localizarLinhaCompativel(true);
        if (linha == null) {
            throw new LineUnavailableException("Nenhum microfone compatível foi detectado neste computador.");
        }
        linhaMicrofone = linha;
        linhaMicrofone.start();

        bufferPcm = new ByteArrayOutputStream();
        gravando = true;

        Thread.ofVirtual().start(() -> {
            byte[] bloco = new byte[4096];
            while (gravando && linhaMicrofone != null) {
                try {
                    int lidos = linhaMicrofone.read(bloco, 0, bloco.length);
                    if (lidos > 0) {
                        bufferPcm.write(bloco, 0, lidos);
                    }
                } catch (Exception e) {
                    break;
                }
            }
        });
    }

    public synchronized byte[] pararEObterWav() throws IOException {
        gravando = false;
        if (linhaMicrofone != null) {
            try {
                linhaMicrofone.stop();
                linhaMicrofone.flush();
                linhaMicrofone.close();
            } catch (Exception ignored) {
            }
            linhaMicrofone = null;
        }
        if (bufferPcm == null || bufferPcm.size() == 0) {
            return new byte[0];
        }

        byte[] pcmBruto = bufferPcm.toByteArray();
        AudioFormat fmt = formatoCapturaAtivo != null ? formatoCapturaAtivo : FORMATO_ALVO_IA;
        byte[] pcmParaWav = normalizarParaMono16Khz(pcmBruto, fmt);
        AudioFormat formatoSaida = pcmParaWav == pcmBruto ? fmt : FORMATO_ALVO_IA;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmParaWav);
             AudioInputStream ais = new AudioInputStream(bais, formatoSaida, pcmParaWav.length / Math.max(1, formatoSaida.getFrameSize()));
             ByteArrayOutputStream wavOut = new ByteArrayOutputStream()) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavOut);
            return wavOut.toByteArray();
        }
    }

    private TargetDataLine localizarLinhaCompativel(boolean abrirLinha) {
        for (AudioFormat fmt : FORMATOS_FALLBACK) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
            if (AudioSystem.isLineSupported(info)) {
                if (!abrirLinha) {
                    return criarPlaceholder(info);
                }
                try {
                    TargetDataLine tdl = (TargetDataLine) AudioSystem.getLine(info);
                    tdl.open(fmt);
                    this.formatoCapturaAtivo = fmt;
                    return tdl;
                } catch (Exception ignored) {
                }
            }
        }
        // Fallback varrendo todos os mixers disponíveis
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mi);
                for (AudioFormat fmt : FORMATOS_FALLBACK) {
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                    if (mixer.isLineSupported(info)) {
                        if (!abrirLinha) {
                            TargetDataLine testLine = (TargetDataLine) mixer.getLine(info);
                            return testLine;
                        }
                        TargetDataLine tdl = (TargetDataLine) mixer.getLine(info);
                        tdl.open(fmt);
                        this.formatoCapturaAtivo = fmt;
                        return tdl;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private TargetDataLine criarPlaceholder(DataLine.Info info) {
        try {
            return (TargetDataLine) AudioSystem.getLine(info);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] normalizarParaMono16Khz(byte[] pcm, AudioFormat origem) {
        if (origem == null || (Math.abs(origem.getSampleRate() - 16000.0f) < 1.0f && origem.getChannels() == 1)) {
            return pcm;
        }
        try {
            int canais = Math.max(1, origem.getChannels());
            int frameSize = Math.max(2 * canais, origem.getFrameSize());
            int totalFramesOrigem = pcm.length / frameSize;
            if (totalFramesOrigem <= 0) return pcm;

            double razao = 16000.0 / origem.getSampleRate();
            int totalFramesDestino = Math.max(1, (int) Math.round(totalFramesOrigem * razao));
            byte[] out = new byte[totalFramesDestino * 2];

            for (int d = 0; d < totalFramesDestino; d++) {
                int sIdx = Math.min(totalFramesOrigem - 1, (int) Math.floor(d / razao));
                int bytePos = sIdx * frameSize;
                int amostra = ((pcm[bytePos + 1] << 8) | (pcm[bytePos] & 0xFF));
                if (canais == 2 && bytePos + 3 < pcm.length) {
                    int amostraDir = ((pcm[bytePos + 3] << 8) | (pcm[bytePos + 2] & 0xFF));
                    amostra = (amostra + amostraDir) / 2;
                }
                out[d * 2] = (byte) (amostra & 0xFF);
                out[d * 2 + 1] = (byte) ((amostra >> 8) & 0xFF);
            }
            return out;
        } catch (Exception e) {
            return pcm;
        }
    }
}
