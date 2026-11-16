package br.com.senac.application.terminal;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Capturador de áudio do microfone padrão (16kHz, 16-bit, mono WAV) para
 * envio de comandos de voz diretamente ao agente IA.
 */
public class CapturadorVozMicrofone {

    private static final AudioFormat FORMATO_VOZ = new AudioFormat(16000.0f, 16, 1, true, false);

    private TargetDataLine linhaMicrofone;
    private ByteArrayOutputStream bufferPcm;
    private volatile boolean gravando = false;

    public boolean isMicrofoneDisponivel() {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMATO_VOZ);
        return AudioSystem.isLineSupported(info);
    }

    public boolean isGravando() {
        return gravando;
    }

    public synchronized void iniciarGravacao() throws LineUnavailableException {
        if (gravando) {
            return;
        }
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMATO_VOZ);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Nenhum microfone compatível (16kHz PCM) foi detectado neste computador.");
        }
        linhaMicrofone = (TargetDataLine) AudioSystem.getLine(info);
        linhaMicrofone.open(FORMATO_VOZ);
        linhaMicrofone.start();

        bufferPcm = new ByteArrayOutputStream();
        gravando = true;

        Thread.ofVirtual().start(() -> {
            byte[] bloco = new byte[4096];
            while (gravando && linhaMicrofone != null) {
                int lidos = linhaMicrofone.read(bloco, 0, bloco.length);
                if (lidos > 0) {
                    bufferPcm.write(bloco, 0, lidos);
                }
            }
        });
    }

    public synchronized byte[] pararEObterWav() throws IOException {
        gravando = false;
        if (linhaMicrofone != null) {
            linhaMicrofone.stop();
            linhaMicrofone.close();
            linhaMicrofone = null;
        }
        if (bufferPcm == null || bufferPcm.size() == 0) {
            return new byte[0];
        }
        byte[] pcmBytes = bufferPcm.toByteArray();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmBytes);
             AudioInputStream ais = new AudioInputStream(bais, FORMATO_VOZ, pcmBytes.length / FORMATO_VOZ.getFrameSize());
             ByteArrayOutputStream wavOut = new ByteArrayOutputStream()) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavOut);
            return wavOut.toByteArray();
        }
    }
}
