# Reconhecimento Facial - Quality Gate Service (CELEPAR)

Serviço de validação e triagem de qualidade de imagem fotográfica desenvolvido em Java sobre o framework Quarkus, utilizando a biblioteca BoofCV para processamento digital de imagens e visão computacional.

---

## 1. Escopo da Funcionalidade

A classe `org.acme.QualityGateService` implementa a validação primária de unicidade facial (Etapa 1 do pipeline de reconhecimento facial). A finalidade do componente é garantir que uma fotografia submetida contenha exatamente um indivíduo enquadrado em primeiro plano antes do prosseguimento para etapas computacionalmente mais onerosas (como extração de embeddings faciais e inferência via redes neurais).

O fluxo de processamento é dividido em quatro etapas:
1. **Extração dos Canais Cromáticos**: Leitura das bandas R, G e B da imagem planar (`Planar<GrayF32>`).
2. **Classificação Cromática de Pele (YCbCr ITU-R BT.601)**: Aplicação de limiarização pontual sobre os canais de crominância azul ($Cb$) e crominância vermelha ($Cr$), gerando uma máscara binária (`GrayU8`).
3. **Morfologia Matemática para Supressão de Ruído**: Operações de erosão (`erode8`) e dilatação (`dilate8`) para remover falsos positivos microscópicos e unificar componentes conexos.
4. **Segmentação e Contagem de Componentes Conexos**: Extração de contornos topológicos com conectividade-8 (`ConnectRule.EIGHT`) e filtragem por área mínima. A imagem é considerada válida se, e somente se, o total de componentes detectados for igual a 1.

---

## 2. Histórico de Problemas Técnicos e Resoluções (Tentativa e Erro)

Durante a configuração do ambiente e implementação inicial, foram diagnosticados e corrigidos os seguintes problemas estruturais:

### 2.1. Dependência do BoofCV Ausente no Classpath de Compilação
- **Sintoma**: Falha no compilador Java com erros do tipo `cannot find symbol` para imports de `boofcv.*`, apesar da biblioteca estar referenciada no arquivo `pom.xml`.
- **Causa Raiz**: A dependência `boofcv-core` havia sido declarada exclusivamente dentro do elemento `<dependencyManagement>`. No padrão Apache Maven, o bloco `<dependencyManagement>` atua apenas como centralizador de versões para builds corporativos e multi-módulos, não incluindo os artefatos no classpath de compilação ou execução do módulo corrente.
- **Resolução**: Inclusão explícita do artefato `boofcv-core` (versão 1.1.2) na seção `<dependencies>`.

### 2.2. Inexistência do Método `ColorYuv.rgbToYcbcr_F32`
- **Sintoma**: Falha durante a compilação do módulo:
  ```text
  [ERROR] QualityGateService.java:[30,17] cannot find symbol
    symbol:   method rgbToYcbcr_F32(boofcv.struct.image.Planar<boofcv.struct.image.GrayF32>, boofcv.struct.image.Planar<boofcv.struct.image.GrayF32>)
    location: class boofcv.alg.color.ColorYuv
  ```
- **Causa Raiz**: A biblioteca BoofCV não disponibiliza na classe `boofcv.alg.color.ColorYuv` nenhum método chamado `rgbToYcbcr_F32` para conversão de imagens planares completas. A classe disponibiliza `ColorYuv.rgbToYuv(...)`, que converte para o espaço YUV analógico contínuo. No espaço YUV analógico, os canais $U$ e $V$ assumem valores centrados em 0 e com escalas que não coincidem com o espaço digital YCbCr de 8 bits normalizado.
- **Resolução**: Substituição da conversão via biblioteca por cálculo aritmético explícito das variáveis $Cb$ e $Cr$ por pixel, diretamente a partir dos canais R, G e B originais, adotando as equações da recomendação ITU-R BT.601.

---

## 3. Racional Técnico da Implementação Atual

### 3.1. Projeção Aritmética Direta de YCbCr
A regra de negócio exige os seguintes intervalos para classificação de pixels de pele:
$$\ 80 \le Cb \le 120 \quad \text{e} \quad 133 \le Cr \le 173\ $$

Esses intervalos são definidos para valores quantizados no padrão digital de 8 bits com offset de 128. Para atender com precisão a essa restrição sem overhead desnecessário de alocação de memória:
- Foram acessadas diretamente as matrizes `GrayF32` dos canais 0 (Red), 1 (Green) e 2 (Blue).
- A conversão utiliza a matriz de transformação ITU-R BT.601:
  $$Cb = -0.168736 \cdot R - 0.331264 \cdot G + 0.500000 \cdot B + 128.0$$
  $$Cr = 0.500000 \cdot R - 0.418688 \cdot G - 0.081312 \cdot B + 128.0$$
- Elimina-se a alocação de um objeto `Planar<GrayF32>` intermediário tridimensional de tamanho idêntico ao da imagem, reduzindo a pressão sobre o coletor de lixo (GC) em cenários de alta concorrência.

### 3.2. Tratamento Morfológico da Máscara Binária
Classificações pontuais em espaço de cor estão sujeitas a ruídos de alta frequência (pixels isolados de tonalidade similar, reflexos especulares e ruído de sensor). Para estabilizar os dados:
- **Erosão 8-conectada (1 iteração)**: Desconecta pequenas pontes de ruído e elimina pixels isolados que não formam blocos consistentes.
- **Dilatação 8-conectada (2 iterações)**: Restaura a dimensão dos componentes verdadeiros e preenche vazios internos nos blocos faciais identificados.

### 3.3. Critério de Segmentação e Limiar de Área
A contagem de faces é realizada via topologia de contorno (`BinaryImageOps.contour`):
- O parâmetro `ConnectRule.EIGHT` avalia vizinhanças ortogonais e diagonais, evitando a fragmentação artificial de uma mesma face.
- O limiar de área mínima foi calibrado em:
  $$\text{Área Mínima} = \frac{\text{Largura} \times \text{Altura}}{20} \quad (5\% \text{ da área total da imagem})$$
- Esse filtro descarta regiões residuais (mãos, fragmentos de pele visíveis no vestuário ou ruídos de fundo) e foca na massa crítica correspondente ao rosto em fotos no padrão documento.

---

## 4. Instruções de Execução

### Pré-requisitos
- JDK 25 instalado e configurado no PATH ou JAVA_HOME.
- Maven 3.9+ (ou utilização do wrapper `./mvnw` incluso).

### Compilação do Projeto
```shell
./mvnw clean compile
```

### Execução em Modo de Desenvolvimento (Quarkus Live Reload)
```shell
./mvnw quarkus:dev
```

### Empacotamento para Produção (Fast-Jar)
```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```
