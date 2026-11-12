package br.com.senac.integracao.ia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.Locale;

/**
 * Detector de capacidade de hardware do dispositivo local (CPU, RAM e GPU)
 * e recomendador de modelos LLM locais (Ollama / LM Studio).
 */
public final class DetectorPerfilHardware {

    public record PerfilHardware(
            boolean temGpuDedicada,
            String nomeDispositivoGpu,
            long memoriaRamTotalGb,
            int nucleosCpu,
            String modeloRecomendado,
            String modeloAlternativoLeve,
            String comandoOllama,
            String justificativaTecnica
    ) {}

    private DetectorPerfilHardware() {}

    public static PerfilHardware detectarPerfil() {
        int nucleos = Runtime.getRuntime().availableProcessors();
        long ramTotalGb = obterRamTotalGb();
        InfoGpu gpu = detectarGpu();

        boolean temGpu = gpu.dedicada();
        String modeloRec;
        String modeloAlt;
        String cmdOllama;
        String justificativa;

        if (temGpu) {
            modeloRec = "qwen2.5:7b";
            modeloAlt = "llama3.1:8b";
            cmdOllama = "ollama run qwen2.5:7b";
            justificativa = String.format(
                    "Dispositivo com aceleração por GPU dedicada (%s). "
                    + "O modelo Qwen 2.5 7B oferece precisão cirúrgica na geração da DSL '$$' e JSON, "
                    + "com processamento em tempo real e consumo de ~4.8 GB de VRAM.",
                    gpu.nome());
        } else {
            // Dispositivo sem GPU dedicada (CPU-only)
            if (ramTotalGb <= 8) {
                modeloRec = "qwen2.5:1.5b";
                modeloAlt = "qwen2.5:3b";
                cmdOllama = "ollama run qwen2.5:1.5b";
                justificativa = String.format(
                        "Dispositivo operando em CPU com %d GB de RAM e %d núcleos. "
                        + "O modelo Qwen 2.5 1.5B consome apenas ~1.1 GB de RAM, "
                        + "garantindo velocidade de 25-40 tokens/s sem sobrecarregar a memória do computador.",
                        ramTotalGb, nucleos);
            } else {
                modeloRec = "qwen2.5:3b";
                modeloAlt = "qwen2.5:1.5b";
                cmdOllama = "ollama run qwen2.5:3b";
                justificativa = String.format(
                        "Dispositivo operando em CPU com %d GB de RAM e %d núcleos. "
                        + "O modelo Qwen 2.5 3B consome apenas ~2.2 GB de RAM em quantização Q4_K_M, "
                        + "gerando respostas a 20-30 tokens/s com excelente raciocínio em português e compreensão das regras fiscais.",
                        ramTotalGb, nucleos);
            }
        }

        return new PerfilHardware(
                temGpu,
                gpu.nome(),
                ramTotalGb,
                nucleos,
                modeloRec,
                modeloAlt,
                cmdOllama,
                justificativa
        );
    }

    private static long obterRamTotalGb() {
        try {
            var osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                long totalBytes = sunOs.getTotalMemorySize();
                return Math.max(1, totalBytes / (1024 * 1024 * 1024));
            }
        } catch (Throwable ignored) {}
        return 8L;
    }

    private record InfoGpu(boolean dedicada, String nome) {}

    private static InfoGpu detectarGpu() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        // 1. Tenta nvidia-smi (placas NVIDIA GeForce / RTX / Quadro / Tesla)
        try {
            Process p = new ProcessBuilder("nvidia-smi", "--query-gpu=name", "--format=csv,noheader").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return new InfoGpu(true, line.trim());
                }
            }
        } catch (Throwable ignored) {}

        // 2. No Windows, consulta wmic ou powershell
        if (os.contains("win")) {
            try {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                        "Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name").start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String s = line.trim().toUpperCase(Locale.ROOT);
                        if (s.contains("NVIDIA") || s.contains("GEFORCE") || s.contains("RTX") || s.contains("GTX")
                                || (s.contains("RADEON") && !s.contains("GRAPHICS") && !s.contains("VEGA"))) {
                            return new InfoGpu(true, line.trim());
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return new InfoGpu(false, "Gráficos Integrados / CPU");
    }
}
