package org.acme; // <-- Ajuste para o pacote do seu projeto, ex: com.suaempresa

import boofcv.alg.color.ColorYuv;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.GThresholdImageOps;
/*import boofcv.struct.ConnectRule; */
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Serviço responsável por validar a qualidade e enquadramento da fotografia
 * (Etapa 1).
 */
@ApplicationScoped
public class QualityGateService {

    /**
     * Valida se existe exatamente UM rosto na imagem usando limiarização de pele
     * (YCbCr).
     * 
     * @param imagemRgb Imagem colorida carregada pelo BoofCV.
     * @return true se passou no teste (apenas 1 rosto), false se falhou.
     */
    public boolean validarUnicidadeFacial(Planar<GrayF32> imagemRgb) {

        // Passo 1: Separar os canais RGB de entrada
        GrayF32 red = imagemRgb.getBand(0);
        GrayF32 green = imagemRgb.getBand(1);
        GrayF32 blue = imagemRgb.getBand(2);

        // Imagem em preto e branco para armazenar a máscara de pele
        GrayU8 mascaraPele = new GrayU8(imagemRgb.width, imagemRgb.height);

        // Passo 2: O filtro "braçal" - Identificar pixels de pele conforme os limiares
        // do documento (ITU-R BT.601)
        for (int y = 0; y < imagemRgb.height; y++) {
            for (int x = 0; x < imagemRgb.width; x++) {
                float r = red.get(x, y);
                float g = green.get(x, y);
                float b = blue.get(x, y);

                // Conversão direta RGB -> Cb e Cr
                float valorCb = -0.168736f * r - 0.331264f * g + 0.5f * b + 128.0f;
                float valorCr = 0.5f * r - 0.418688f * g - 0.081312f * b + 128.0f;

                // Regra do documento: 80 <= Cb <= 120 e 133 <= Cr <= 173
                if ((valorCb >= 80 && valorCb <= 120) && (valorCr >= 133 && valorCr <= 173)) {
                    mascaraPele.set(x, y, 1); // É pele!
                } else {
                    mascaraPele.set(x, y, 0); // Não é pele
                }
            }
        }

        // Operações Morfológicas (Limpeza): Tira os ruídos pequenos da imagem
        GrayU8 mascaraPeleLimpa = BinaryImageOps.erode8(mascaraPele, 1, null);
        mascaraPeleLimpa = BinaryImageOps.dilate8(mascaraPeleLimpa, 2, null);

        // Passo 3: Encontrar os "blobs" (As manchas grandes conectadas que formam
        // rostos)
        List<Contour> blobs = BinaryImageOps.contour(mascaraPeleLimpa, ConnectRule.EIGHT, null);

        // Filtra blobs pequenos (ignora ruídos que sobraram)
        int rostosEncontrados = 0;
        int areaMinima = (imagemRgb.width * imagemRgb.height) / 20; // Pelo menos 5% da imagem (ajustar conforme
                                                                    // necessidade)

        for (Contour blob : blobs) {
            // Conta os pontos do contorno para estimar a área de forma grosseira.
            // Numa implementação final pode-se usar geometria exata do BoofCV.
            if (blob.external.size() > areaMinima) {
                rostosEncontrados++;
            }
        }

        // Passo 4: O IF da decisão final
        if (rostosEncontrados == 1) {
            return true; // Sucesso: Rosto validado!
        } else {
            System.out.println("Falha na Unicidade: Foram detectados " + rostosEncontrados + " rostos.");
            return false; // Falhou: Zero rostos ou mais de 1.
        }
    }
}