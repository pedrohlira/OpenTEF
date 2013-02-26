package br.com.phdss;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.io.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import javax.swing.JOptionPane;
import org.apache.log4j.Logger;
import org.ini4j.Wini;

/**
 * Classe que representa o TEF no sistema e todas suas funcionalidiades.
 *
 * @author Pedro H. Lira
 */
public class TEF {

    private static final String CRLF = "\r\n";
    private static Logger log;
    private static String reqIntPos001;
    private static String respIntPos001;
    private static String respIntPosSts;
    private static String relatorio;
    private static String titulo;
    private static String nome;
    private static String versao;
    private static String razao;
    private static Map<String, String> dados;
    private static FilenameFilter filtro;
    private static File pathTmp;
    private static boolean folhaDupla;
    private static boolean linhaAlinha;

    /**
     * Construtor padrao.
     */
    private TEF() {
    }

    /**
     * Metodo que seta os dados de configuracao.
     *
     * @param config o mapa de dados de config.
     */
    public static void setTEF(Map<String, String> config) {
        log = Logger.getLogger(TEF.class);
        reqIntPos001 = config.get("tef.req") + "IntPos.001";
        respIntPos001 = config.get("tef.resp") + "IntPos.001";
        respIntPosSts = config.get("tef.resp") + "IntPos.Sts";
        relatorio = config.get("ecf.reltef");
        titulo = config.get("tef.titulo");
        pathTmp = new File(config.get("tef.tmp"));
        folhaDupla = config.get("ecf.folhas").equals("2");
        linhaAlinha = Boolean.valueOf(config.get("tef.linha"));
        nome = config.get("paf.nome");
        versao = config.get("paf.versao");
        razao = config.get("sh.razao");
        dados = null;
        
        // filtro de arquivos
        filtro = new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".txt");
            }
        };

        // caso o diretorio tmp do tef nao esteja criado
        if (pathTmp.exists() == false) {
            pathTmp.mkdir();
        }
    }

    /**
     * Metodo que trava o teclado, mouse e outras entradas de dados do usuario.
     *
     * @param trava se passar True ele trava, se passar False ele destrava.
     */
    public static void bloquear(Boolean trava) {
        if (Platform.isWindows()) {
            NativeLibrary lib = NativeLibrary.getInstance("user32");
            Function fun = lib.getFunction("BlockInput");
            if (trava) {
                fun.invoke(new Object[]{"Blk"});
            } else {
                fun.invoke(new Object[]{0});
            }
        }
    }

    /**
     * Metodo que seta o foca na janela pelo nome.
     *
     * @param titulo o nome da janela.
     */
    public static void focar(String titulo) {
        if (Platform.isWindows()) {
            User32 user32 = User32.INSTANCE;
            HWND win = user32.FindWindow(null, titulo);
            user32.SetFocus(win);
        }
    }

    /**
     * Metodo que le os dados que um arquivo.
     *
     * @param arquivo o path completo do arquivo a se lido.
     * @param tempo o tempo maximo de espera em segundos.
     * @return o texto lido ou null caso nao consiga ler.
     */
    public static String lerArquivo(String arquivo, int tempo) {
        String ret = null;

        try {
            File f = new File(arquivo);
            for (int i = 0; i < tempo; i++) {
                if (f.exists()) {
                    break;
                }
                Thread.sleep(1000);
            }

            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    StringBuilder sb = new StringBuilder();
                    while (br.ready()) {
                        sb.append(br.readLine()).append(CRLF);
                    }
                    ret = sb.toString();
                }
            }
        } catch (InterruptedException | IOException ex) {
            log.error("Erro ao ler arquivo do TEF", ex);
            ret = null;
        }

        return ret;
    }

    /**
     * Metodo que salva um arquivo no caminho especificado.
     *
     * @param arquivo o path completo do arquivo a ser salvo.
     * @param texto o texto interno do arquivo a ser gravado.
     */
    public static void salvarArquivo(String arquivo, String texto) {
        File tmp = new File(arquivo);
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write(texto);
            fw.flush();
        } catch (IOException ex) {
            log.error("Erro ao salvar arquivo do TEF.", ex);
        }
    }

    /**
     * Metodo que deleta o arquivo pendente com o id passado por parametro.
     *
     * @param id deve-se passar o identificador do pendente.
     */
    public static void deletarPendente(String id) {
        deletarArquivo(pathTmp.getAbsolutePath() + System.getProperty("file.separator") + "pendente" + id + ".txt");
    }

    /**
     * Metodo que deleta o arquivo passado por parametro.
     *
     * @param arquivo deve-se passar o caminho completo.
     */
    public static void deletarArquivo(String arquivo) {
        File f = new File(arquivo);
        f.delete();
    }

    /**
     * Metodo que limpa os arquivos dos paths.
     */
    public static void limpar() {
        deletarArquivo(respIntPosSts);
        dados = null;
    }

    /**
     * Metodo que cancela (Nao Confirma) as transacoes pendentes ou backups, as
     * quais nao precisam de impressao de comprovante.
     *
     * @throws Exception dispara uma excecao caso ocorra algum erro.
     */
    public static void cancelarPendentes(boolean auto) throws Exception {
        // verificando arquivo principal de resposta
        String principal = lerArquivo(respIntPos001, 0);
        if (principal != null) {
            Map<String, String> mapa = iniToMap(principal);
            String id = mapa.get("001-000");
            confirmarTransacao(id, false);
        }

        // arquivos pendentes
        for (File arquivo : pathTmp.listFiles(filtro)) {
            if (arquivo.getName().startsWith("pendente")) {
                String id = arquivo.getName().replaceAll("\\D", "");
                confirmarTransacao(id, false);
            } else if (auto && arquivo.getName().startsWith("backup")) {
                // retorna os dados do arquivo
                String resp = lerArquivo(arquivo.getAbsolutePath(), 0);

                if (resp != null) {
                    Map<String, String> back = iniToMap(resp);
                    if (back.get("000-000").equals("CRT")) {
                        // cancela a transacao que foi confirmada e nao impressa.
                        Double valor = Double.valueOf(back.get("003-000")) / 100;
                        Date data = new SimpleDateFormat("ddMMyyyyHHmmss").parse(back.get("022-000") + back.get("023-000"));
                        String id = gerarId();
                        cancelarTransacao(id, valor, back.get("010-000"), back.get("012-000"), data);

                        try {
                            ECF.enviar(EComandoECF.ECF_FechaRelatorio);
                            ECF.enviar(EComandoECF.ECF_AbreRelatorioGerencial, relatorio);
                            TEF.imprimirVias(TEF.getDados(), EComandoECF.ECF_LinhaRelatorioGerencial);
                            ECF.enviar(EComandoECF.ECF_FechaRelatorio);
                            confirmarTransacao(id, true);
                            arquivo.delete();
                        } catch (Exception ex) {
                            ECF.enviar(EComandoECF.ECF_FechaRelatorio);
                            confirmarTransacao(id, false);
                        }
                    } else {
                        arquivo.delete();
                    }
                } else {
                    log.error("Nao encontrou o arquivo informado.");
                    throw new Exception("Nao encontrou o arquivo informado.");
                }
            } else {
                arquivo.delete();
            }
        }
    }

    /**
     * Metodo que verifica se o Gerenciador Padrao esta ativo.
     *
     * @return retorna true se verdadeiro, falso caso contrario, deve-se mostrar
     * uma mensagem ao operador caso nao esteja.
     * @exception Exception caso nao consiga ler ou escrever os arquivos para o
     * GP.
     */
    public static boolean gpAtivo() {
        limpar();

        // montando o comando
        StringBuilder sb = new StringBuilder();
        sb.append("000-000 = ATV").append(CRLF);
        sb.append("001-000 = ").append(gerarId()).append(CRLF);
        sb.append("999-999 = 0").append(CRLF);

        // gravando no arquivo
        salvarArquivo(reqIntPos001, sb.toString());
        boolean resp = lerArquivo(respIntPosSts, 7) != null;
        deletarArquivo(respIntPosSts);
        return resp;
    }

    /**
     * Metodo que realiza a transacao com cartao de credito. Sera gravado no
     * path tpm informado o arquivo pendente[id].txt para ser usado na
     * confirmacao ou nao e tambem caso precise cancelar
     *
     * @param id um novo identificador unico da transacao.
     * @param COO o numero do cupom ao qual o pagamento sera vinculado.
     * @param valor o valor a ser pago usando esta transacao.
     * @throws Exception caso a resposta do gerenciado avise sobre falhas.
     */
    public static void realizarTransacao(String id, String COO, double valor) throws Exception {
        // montando o comando
        StringBuilder sb = new StringBuilder();
        sb.append("000-000 = CRT").append(CRLF);
        sb.append("001-000 = ").append(id).append(CRLF);
        sb.append("003-000 = ").append(formatarValor(valor)).append(CRLF);
        sb.append("004-000 = 0").append(CRLF);
        sb.append("701-000 = ").append(nome).append(" ").append(versao).append(CRLF);
        sb.append("706-000 = 2").append(CRLF);
        sb.append("716-000 = ").append(razao).append(CRLF);
        //sb.append("777-777 = teste REDCARD").append(CRLF); // usado somente na homologacao
        sb.append("999-999 = 0").append(CRLF);
        comandoTEF(id, sb.toString(), false);
    }

    /**
     * Metodo que realiza a confirmacao ou nao da trasacao realizada.
     *
     * @param id o identificador unico da transacao que deseja confirmar. sera
     * usar o arquivo pendente[id].txt no path tmp informado para recuperar os
     * demais dados da transacao realizada.
     * @param confirma informa true se for para confirmar e false caso
     * contrario. Se passado true guarde os dados para futuro cancelamento.
     * @throws Exception caso nao seja encontrado o arquivo pendente[id].txt no
     * path tmp ou falha no GP.
     */
    public static void confirmarTransacao(String id, boolean confirma) throws Exception {
        // le o arquivo pendente
        String resp = lerArquivo(respIntPos001, 0);
        if (resp == null) {
            resp = lerArquivo(pathTmp.getAbsolutePath() + System.getProperty("file.separator") + "pendente" + id + ".txt", 0);
        }
        Map<String, String> conf = resp != null ? iniToMap(resp) : null;

        // montando o comando
        String id2 = gerarId();
        StringBuilder sb = new StringBuilder();
        sb.append("000-000 = ").append(confirma ? "CNF" : "NCN").append(CRLF);
        sb.append("001-000 = ").append(id2).append(CRLF);
        sb.append("010-000 = ").append(conf.get("010-000")).append(CRLF);
        sb.append("012-000 = ").append(conf.get("012-000")).append(CRLF);
        sb.append("027-000 = ").append(conf.get("027-000")).append(CRLF);
        sb.append("999-999 = 0").append(CRLF);
        comandoTEF(id2, sb.toString(), true);

        // se confirmou ou nao deleta os arquivos
        deletarArquivo(reqIntPos001);
        deletarArquivo(respIntPosSts);
        deletarArquivo(respIntPos001);
        deletarPendente(id);

        // caso NAO CONFIRMACAO, emite a mensagem
        if (!confirma) {
            double valor = conf.get("003-000") != null ? Double.valueOf(conf.get("003-000")) / 100 : 0.00;
            mensagem(conf.get("010-000"), conf.get("012-000"), valor);
        }
    }

    /**
     * Metodo que realiza o cancelamento de um transacao que ja foi confirmada.
     *
     * @param id um novo identificador unico da transacao.
     * @param valor o valor da transacao.
     * @param rede a rede usada na transacao.
     * @param nsu o identificador unico da operadora retornado anteriormente.
     * @param data a data completa com hora que foi retornado anteiormente pela
     * operadora.
     * @throws Exception caso a resposta do gerenciado avise sobre falhas.
     */
    public static void cancelarTransacao(String id, double valor, String rede, String nsu, Date data) throws Exception {
        // montando o comando
        StringBuilder sb = new StringBuilder();
        sb.append("000-000 = CNC").append(CRLF);
        sb.append("001-000 = ").append(id).append(CRLF);
        sb.append("003-000 = ").append(formatarValor(valor)).append(CRLF);
        sb.append("004-000 = 0").append(CRLF);
        sb.append("010-000 = ").append(rede).append(CRLF);
        sb.append("012-000 = ").append(nsu).append(CRLF);
        sb.append("022-000 = ").append(new SimpleDateFormat("ddMMyyyy").format(data)).append(CRLF);
        sb.append("023-000 = ").append(new SimpleDateFormat("HHmmss").format(data)).append(CRLF);
        sb.append("701-000 = ").append(nome).append(" ").append(versao).append(CRLF);
        sb.append("706-000 = 2").append(CRLF);
        sb.append("716-000 = ").append(razao).append(CRLF);
        sb.append("999-999 = 0").append(CRLF);
        comandoTEF(id, sb.toString(), true);
    }

    /**
     * Metodo que realiza a transacao de consulta de cheque.
     *
     * @param id um novo identificador unico da transacao.
     * @param valor o valor a ser usado esta transacao.
     * @throws Exception caso a resposta do gerenciado avise sobre falhas.
     */
    public static void consultarCheque(String id, double valor) throws Exception {
        // montando o comando
        StringBuilder sb = new StringBuilder();
        sb.append("000-000 = CHQ").append(CRLF);
        sb.append("001-000 = ").append(id).append(CRLF);
        sb.append("003-000 = ").append(formatarValor(valor)).append(CRLF);
        sb.append("004-000 = 0").append(CRLF);
        sb.append("701-000 = ").append(nome).append(" ").append(versao).append(CRLF);
        sb.append("706-000 = 2").append(CRLF);
        sb.append("716-000 = ").append(razao).append(CRLF);
        sb.append("999-999 = 0").append(CRLF);
        comandoTEF(id, sb.toString(), false);
    }

    /**
     * Metodo que realiza a abertura da tela de ADM do GP.
     *
     * @throws Exception caso a resposta do gerenciado avise sobre falhas.
     */
    public static void abrirADM(String id) throws Exception {
        // montando o comando
        StringBuilder sb = new StringBuilder();
        sb.append("000-000 = ADM").append(CRLF);
        sb.append("001-000 = ").append(id).append(CRLF);
        sb.append("701-000 = ").append(nome).append(" ").append(versao).append(CRLF);
        sb.append("706-000 = 2").append(CRLF);
        sb.append("716-000 = ").append(razao).append(CRLF);
        sb.append("999-999 = 0").append(CRLF);
        comandoTEF(id, sb.toString(), false);
    }

    /**
     * Metodo que gera um idenficador unico para as transacoes.
     *
     * @return um texto unico para ser usado como ID.
     */
    public static String gerarId() {
        try {
            Thread.sleep(1000);
            return new SimpleDateFormat("MMddHHmmss").format(new Date());
        } catch (InterruptedException ex) {
            String id = new Date().getTime() + "";
            return id.substring(id.length() - 10);
        }
    }

    /**
     * Metodo que le os dados do arquivo de resposta do GP.
     *
     * @param id o identificador unico da transacao.
     * @return um mapa de dados em formatos de String.
     */
    public static boolean lerResposta(String id) throws Exception {
        boolean ret = false;
        String resp = lerArquivo(respIntPos001, 0);
        dados = resp != null ? iniToMap(resp) : null;

        if (dados != null) {
            // confirma se e da mesma transacao
            if (dados.get("001-000").equals(id)) {
                // analisa resposta
                if (dados.get("009-000").equals("0")) {
                    // salva o arquivo pendente
                    salvarArquivo(pathTmp.getAbsolutePath() + System.getProperty("file.separator") + "pendente" + id + ".txt", resp);
                    ret = true;
                } else if (!dados.get("030-000").equals("")) {
                    deletarArquivo(respIntPos001);
                    throw new Exception(dados.get("030-000"));
                }
            } else {
                deletarArquivo(respIntPos001);
            }
        }
        return ret;
    }

    /**
     * Metodo que somente ler um arquivo e coloca no formato de Map de Strings
     *
     * @param texto o texto lido do arquivo.
     * @return um Mapa de Strings com chave/valor.
     * @exception Exception dispara caso nao consiga transformar o texto.
     */
    public static Map<String, String> iniToMap(String texto) throws Exception {
        // pega os dados
        try {
            StringBuilder sb = new StringBuilder("[TEF]").append(CRLF).append(texto);
            InputStream stream = new ByteArrayInputStream(sb.toString().getBytes());
            Wini ini = new Wini(stream);
            return ini.get("TEF");
        } catch (Exception ex) {
            throw new Exception("Dados do arquivo, não são compatíveis.");
        }
    }

    /**
     * Metodo que formata o valor da maneira que o GP espera
     *
     * @param valor o valor a ser processado.
     * @return uma String com duas casas decimais, sem separador.
     */
    public static String formatarValor(double valor) {
        NumberFormat nf = NumberFormat.getIntegerInstance();
        nf.setMinimumIntegerDigits(1);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(false);
        return nf.format(valor).replace(",", "");
    }

    /**
     * Metodo que mostra a mensagem na tela quando cencela ou nao confirma.
     *
     * @param rede o nome da rede.
     * @param nsu o numero do nsu.
     * @param valor o valor declarado.
     */
    public static void mensagem(String rede, String nsu, double valor) {
        StringBuilder sb = new StringBuilder("Última Transação TEF foi cancelada!").append(CRLF).append(CRLF);
        sb.append("Rede: ").append(rede).append(CRLF);
        if (nsu != null && !nsu.equals("")) {
            sb.append("NSU: ").append(nsu).append(CRLF);
        }
        if (valor > 0) {
            sb.append("Valor: ").append(NumberFormat.getCurrencyInstance().format(valor));
        }
        bloquear(false);
        JOptionPane.showMessageDialog(null, sb.toString(), "TEF", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Metodo que imprime as vias do Cartao.
     *
     * @param dados o mapa de dados recuperado do arquivo.
     * @param comando o tipo de comando de linha, dependendo se for a primeira
     * vez sera CC, caso contrario RG
     * @throws Exception caso seja interrompido e seja solicitado para cancelar.
     */
    public static void imprimirVias(Map<String, String> dados, EComandoECF comando) throws Exception {
        int linhas;
        String chave;
        boolean imprimiu = false;

        if (folhaDupla) {
            // unica via
            linhas = Integer.valueOf(dados.get("028-000"));
            chave = "029-";
        } else {
            // 1ª via
            if (dados.get("710-000") != null && !dados.get("710-000").equals("0")) {
                linhas = Integer.valueOf(dados.get("710-000"));
                chave = "711-";
            } else if (dados.get("712-000") != null && !dados.get("712-000").equals("0")) {
                linhas = Integer.valueOf(dados.get("712-000"));
                chave = "713-";
            } else {
                linhas = Integer.valueOf(dados.get("028-000"));
                chave = "029-";
            }
        }

        while (imprimiu == false) {
            imprimiu = imprimirVia(dados, comando, linhas, chave);
            if (imprimiu && folhaDupla == false) {
                // 2ª via
                if (dados.get("714-000") != null && !dados.get("714-000").equals("0")) {
                    linhas = Integer.valueOf(dados.get("714-000"));
                    chave = "715-";
                } else {
                    linhas = Integer.valueOf(dados.get("028-000"));
                    chave = "029-";
                }

                // segunda via para todos exceto relatorio de fechamento
                if (!dados.get("011-000").equals("1")) {
                    ECF.enviar(EComandoECF.ECF_PulaLinhas, "3");
                    imprimiu = imprimirVia(dados, comando, linhas, chave);
                    if (imprimiu) {
                        ECF.enviar(EComandoECF.ECF_PulaLinhas, "3");
                    }
                }
            }
        }
    }

    /**
     * Metodo que imprime uma via da Cartao.
     *
     * @param dados o mapa de dados recuperado do arquivo.
     * @param comando o tipo de comando de linha, dependendo se for a primeira
     * vez sera CC, caso contrario RG
     * @param linhas o numero de linhas que serao impressos.
     * @param chave o prefixo do campo contendo as linhas a serem impressas.
     * @return true se impressao da via com sucesso, falso para reimpressao
     * @throws Exception caso dispare interromper a impressao e cancela os
     * camandos anteriores.
     */
    private static boolean imprimirVia(Map<String, String> dados, EComandoECF comando, int linhas, String chave) throws Exception {
        boolean ret = true;
        StringBuilder sb = new StringBuilder();

        // imprime as linhas
        for (int linha = 1; linha <= linhas; linha++) {
            String indice = chave + (linha < 100 ? "0" : "");
            indice += linha < 10 ? "0" : "";
            indice += linha;

            // verifica se faz linha-a-linha ou mando todo o texto de uma vez
            String[] resp = null;
            if (linhaAlinha) {
                resp = ECF.enviar(comando, dados.get(indice));
            } else {
                sb.append(dados.get(indice).replace("\"", "")).append(ECF.SL);
                if (linha == linhas) {
                    resp = ECF.enviar(comando, sb.toString());
                }
            }

            // caso tenha enviado e resposta com erro
            while (resp != null && ECF.ERRO.equals(resp[0])) {
                bloquear(false);
                int escolha = JOptionPane.showOptionDialog(null, "Impressora não responde, tentar novamente?", "TEF",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, new String[]{"SIM", "NÃO"}, JOptionPane.YES_OPTION);
                bloquear(true);
                if (escolha == JOptionPane.YES_OPTION) {
                    ret = false;
                    // fecha/ abre
                    resp = ECF.enviar(EComandoECF.ECF_FechaRelatorio);
                    if (ECF.OK.equals(resp[0])) {
                        resp = ECF.enviar(EComandoECF.ECF_AbreRelatorioGerencial, relatorio);
                        if (ECF.OK.equals(resp[0])) {
                            break;
                        }
                    }
                } else {
                    log.error("Erro ao imprimir linha do relatorio. -> " + resp[1]);
                    throw new Exception(resp[1]);
                }
            }

            // se teve problemas e vai re-imprimir, sai do loop
            if (ret == false) {
                break;
            }
        }

        return ret;
    }

    /**
     * Metodo geral que efetiva o comando ao TEF.
     *
     * @param id o identificador da transacao.
     * @param comando o comando a ser executado completo.
     * @param trava informa se a trava de teclado esta ativa.
     * @throws Exception dispara em caso de erro.
     */
    public static void comandoTEF(String id, String comando, boolean trava) throws Exception {
        boolean ret;

        do {
            ret = gpAtivo();
            // GP ativo
            if (ret) {
                // gravando no arquivo
                salvarArquivo(reqIntPos001, comando);
                // verifica se o GP recebeu
                lerArquivo(respIntPosSts, 7);
                deletarArquivo(respIntPosSts);
                String tipoComando = comando.substring(0, 13);
                if (!(tipoComando.endsWith("CNF") || tipoComando.endsWith("NCN"))) {
                    // foca no GP
                    focar(titulo);
                    do {
                        Thread.sleep(1000);
                    } while (!lerResposta(id));
                }
            } else {
                bloquear(false);
                JOptionPane.showMessageDialog(null, "Gerenciador Padrão não está ativo!\nPor favor ative-o para continuar.", "TEF", JOptionPane.INFORMATION_MESSAGE);
                bloquear(trava);
            }
        } while (!ret);
    }

    public static Map<String, String> getDados() {
        return dados;
    }

    public static void setDados(Map<String, String> dados) {
        TEF.dados = dados;
    }

    public static FilenameFilter getFiltro() {
        return filtro;
    }

    public static void setFiltro(FilenameFilter filtro) {
        TEF.filtro = filtro;
    }

    public static File getPathTmp() {
        return pathTmp;
    }

    public static void setPathTmp(File pathTmp) {
        TEF.pathTmp = pathTmp;
    }

    public static String getReqIntPos001() {
        return reqIntPos001;
    }

    public static void setReqIntPos001(String reqIntPos001) {
        TEF.reqIntPos001 = reqIntPos001;
    }

    public static String getRespIntPos001() {
        return respIntPos001;
    }

    public static void setRespIntPos001(String respIntPos001) {
        TEF.respIntPos001 = respIntPos001;
    }

    public static String getRespIntPosSts() {
        return respIntPosSts;
    }

    public static void setRespIntPosSts(String respIntPosSts) {
        TEF.respIntPosSts = respIntPosSts;
    }
}
