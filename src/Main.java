import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;

public class Main extends JFrame {

    // Gerenciador de Telas (Menu -> Jogo)
    private CardLayout cards = new CardLayout();
    private JPanel painelPrincipal = new JPanel(cards);
    private GamePanel painelJogo;

    public Main() {
        setTitle("Eco Toss: O Desafio do Estilingue");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 750); 
        setLocationRelativeTo(null);
        setResizable(false);

        // Cria as duas telas
        JPanel menu = criarMenu();
        painelJogo = new GamePanel();

        // Adiciona ao organizador de telas
        painelPrincipal.add(menu, "MENU");
        painelPrincipal.add(painelJogo, "JOGO");

        setContentPane(painelPrincipal);
        cards.show(painelPrincipal, "MENU");
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }

    // =====================================================================
    // SISTEMA DE ÁUDIO GLOBAL (CORRIGIDO PARA .WAV E CAMINHOS SEGUROS)
    // =====================================================================
    public static void tocarSom(String caminho) {
        // Roda o som em uma thread separada para não travar os gráficos do jogo
        new Thread(() -> {
            try {
                // Tenta achar o arquivo em vários caminhos possíveis (igual as imagens)
                String[] possibilidades = { caminho, "../" + caminho, "java/01/" + caminho, "01/" + caminho };
                File arquivoSom = null;
                
                for (String p : possibilidades) {
                    File f = new File(p);
                    if (f.exists()) {
                        arquivoSom = f;
                        break;
                    }
                }

                if (arquivoSom != null) {
                    AudioInputStream audioInput = AudioSystem.getAudioInputStream(arquivoSom);
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioInput);
                    clip.start();
                } else {
                    System.out.println("Aviso: Arquivo de som não encontrado: " + caminho);
                }
            } catch (Exception e) {
                System.out.println("Erro ao reproduzir o som (" + caminho + "): Formato não suportado. Lembre-se de usar .WAV!");
            }
        }).start();
    }

    // =====================================================================
    // TELA DE MENU (SELEÇÃO DE DIFICULDADE)
    // =====================================================================
    private JPanel criarMenu() {
        JPanel menu = new JPanel(new GridBagLayout());
        menu.setBackground(new Color(30, 40, 60)); // Azul escuro
        
        JPanel painelBotoes = new JPanel(new GridLayout(5, 1, 10, 15));
        painelBotoes.setOpaque(false);

        JLabel titulo = new JLabel("ECO TOSS", SwingConstants.CENTER);
        titulo.setForeground(Color.WHITE);
        titulo.setFont(new Font("Arial", Font.BOLD, 40));
        painelBotoes.add(titulo);

        // Configuração dos Botões: Nome | Frames da Mira (Comprimento)
        adicionarBotaoDificuldade(painelBotoes, "FÁCIL (Mira Completa)", 90, new Color(50, 150, 80));
        adicionarBotaoDificuldade(painelBotoes, "NORMAL (Mira Curta)", 45, new Color(200, 150, 20));
        adicionarBotaoDificuldade(painelBotoes, "DIFÍCIL (Quase cego)", 15, new Color(200, 80, 20));
        adicionarBotaoDificuldade(painelBotoes, "REALISTA (Sem Mira)", 0, new Color(150, 30, 30));

        menu.add(painelBotoes);
        return menu;
    }

    private void adicionarBotaoDificuldade(JPanel painel, String texto, int framesMira, Color cor) {
        JButton btn = new JButton(texto);
        btn.setFont(new Font("Arial", Font.BOLD, 20));
        btn.setBackground(cor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        btn.addActionListener(e -> {
            painelJogo.iniciarJogo(framesMira);
            cards.show(painelPrincipal, "JOGO");
            tocarSom("audios/acerto.wav"); // CORRIGIDO PARA .WAV
        });
        painel.add(btn);
    }

    // =====================================================================
    // MOTOR DO JOGO (GAME PANEL)
    // =====================================================================
    class GamePanel extends JPanel implements ActionListener, MouseMotionListener, MouseListener {

        Timer timer = new Timer(16, this); 
        Random random = new Random();

        Lixeira[] lixeiras;
        Objeto objetoAtual;
        ArrayList<ItemData> itens = new ArrayList<>();

        // Status do Jogo
        int pontos = 0, vidas = 5, fase = 1, combo = 0;
        int tamanhoMira = 90; 

        // Controles de Mouse
        double mouseX = 500, mouseY = 500;

        // Controles de Tempo (Fases 2 e 3)
        long inicioRodada = 0, tempoLimite = 0;

        // Feedback Visual
        String aviso = "";
        long avisoAte = 0;

        // Variáveis de Movimento
        int grupoOffset = 0, grupoDir = 1;
        int estilingueX = 150; 
        int estilingueDir = 1;

        final int CHAO_Y = 550; 

        // Fontes
        Font fonteHUD = new Font("Arial", Font.BOLD, 20);
        Font fonteHUDAux = new Font("Arial", Font.PLAIN, 14);
        Font fonteAviso = new Font("Arial", Font.BOLD, 30);

        public GamePanel() {
            addMouseMotionListener(this);
            addMouseListener(this);

            lixeiras = new Lixeira[]{
                    new Lixeira(350, CHAO_Y - 95, 110, 95, "PAPEL", new Color(50, 110, 220)),
                    new Lixeira(470, CHAO_Y - 95, 110, 95, "PLASTICO", new Color(220, 50, 50)),
                    new Lixeira(590, CHAO_Y - 95, 110, 95, "VIDRO", new Color(50, 160, 70)),
                    new Lixeira(710, CHAO_Y - 95, 110, 95, "METAL", new Color(230, 180, 10)),
                    new Lixeira(830, CHAO_Y - 95, 110, 95, "ORGANICO", new Color(130, 80, 50))
            };

            carregarItensLocais();
        }

        public void iniciarJogo(int tamanhoMiraEscolhida) {
            this.tamanhoMira = tamanhoMiraEscolhida;
            pontos = 0; vidas = 5; fase = 1; combo = 0;
            estilingueX = 150; grupoOffset = 0;
            novoObjeto();
            timer.start();
        }

        private void carregarItensLocais() {
            adicionarItem("PAPEL", "imagens/jornal.png");
            adicionarItem("PLASTICO", "imagens/garrafa.png");
            adicionarItem("VIDRO", "imagens/garrafa_vidro.png");
            adicionarItem("METAL", "imagens/lata.png");
            adicionarItem("METAL", "imagens/metal.png");
            adicionarItem("ORGANICO", "imagens/Banana.png");
            adicionarItem("ORGANICO", "imagens/maca.png");
        }

        private void adicionarItem(String tipo, String caminho) {
            itens.add(new ItemData(tipo, caminho));
        }

        private void novoObjeto() {
            if (itens.isEmpty()) return;
            ItemData item = itens.get(random.nextInt(itens.size()));
            objetoAtual = new Objeto(item.tipo, item.caminho, estilingueX + 2, CHAO_Y - 80);
            inicioRodada = System.currentTimeMillis();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            verificarEvolucaoFase();
            moverCenario();

            if (objetoAtual != null) {
                objetoAtual.atualizar();

                // Fim de Tempo
                if (fase >= 2 && !objetoAtual.voando && !objetoAtual.sendoArrastado) {
                    if (System.currentTimeMillis() - inicioRodada > tempoLimite) {
                        perderVida("TEMPO ESGOTADO!");
                        return;
                    }
                }

                // Colisão com as lixeiras
                if (objetoAtual.voando && objetoAtual.vy > 0) { 
                    for (Lixeira l : lixeiras) {
                        if (objetoAtual.getBounds().intersects(l.getHitboxTop())) {
                            if (objetoAtual.tipo.equals(l.tipo)) {
                                combo++;
                                pontos += (combo >= 3) ? 15 : 10;
                                tocarSom("audios/acerto.wav"); // CORRIGIDO PARA .WAV
                                novoObjeto();
                            } else {
                                perderVida("LIXEIRA ERRADA!");
                            }
                            return;
                        }
                    }
                }

                // Errou e bateu no chão
                if (objetoAtual.y + objetoAtual.altura > CHAO_Y + 150 || objetoAtual.x > 1100 || objetoAtual.x < -150) {
                    perderVida("ERROU O ALVO!");
                    return;
                }
            }
            repaint();
        }

        private void verificarEvolucaoFase() {
            int nova = 1;
            if (pontos >= 100) nova = 2;
            if (pontos >= 250) nova = 3;
            if (nova > fase) {
                fase = nova;
                mostrarAviso(fase == 2 ? "FASE 2: ALVOS MÓVEIS" : "FASE 3: CAOS TOTAL");
            }
            if (fase >= 2) tempoLimite = Math.max(3000, 8000 - (pontos * 12));
        }

        private void moverCenario() {
            if (fase >= 2) {
                grupoOffset += grupoDir * 3;
                if (grupoOffset < -40 || grupoOffset > 40) grupoDir *= -1;
                int[] baseX = {350, 470, 590, 710, 830};
                for (int i = 0; i < lixeiras.length; i++) lixeiras[i].x = baseX[i] + grupoOffset;
            }
            if (fase >= 3 && objetoAtual != null && !objetoAtual.voando && !objetoAtual.sendoArrastado) {
                estilingueX += estilingueDir * 2;
                if (estilingueX < 50 || estilingueX > 200) estilingueDir *= -1;
                objetoAtual.x = estilingueX + 2;
            }
        }

        private void perderVida(String motivo) {
            vidas--; combo = 0; 
            mostrarAviso(motivo);
            tocarSom("audios/erro.wav"); // CORRIGIDO PARA .WAV

            if (vidas <= 0) {
                mostrarAviso("GAME OVER: " + pontos + " PONTOS");
                timer.stop();
                Timer t = new Timer(3000, ev -> {
                    cards.show(painelPrincipal, "MENU"); 
                }); 
                t.setRepeats(false); 
                t.start();
            } else {
                novoObjeto();
            }
        }

        private void mostrarAviso(String texto) { aviso = texto; avisoAte = System.currentTimeMillis() + 1500; }

        @Override
        public void mousePressed(MouseEvent e) {
            if (objetoAtual != null && !objetoAtual.voando && objetoAtual.getBounds().contains(e.getPoint())) {
                objetoAtual.sendoArrastado = true;
                tocarSom("audios/esticar.wav"); // CORRIGIDO PARA .WAV
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (objetoAtual != null && objetoAtual.sendoArrastado) {
                double centroX = estilingueX + 35;
                double centroY = CHAO_Y - 50;
                
                double dist = Point.distance(centroX, centroY, e.getX(), e.getY());
                if (dist > 150) { 
                    double angulo = Math.atan2(e.getY() - centroY, e.getX() - centroX);
                    objetoAtual.x = centroX + Math.cos(angulo) * 150 - 35;
                    objetoAtual.y = centroY + Math.sin(angulo) * 150 - 35;
                } else {
                    objetoAtual.x = e.getX() - 35;
                    objetoAtual.y = e.getY() - 35;
                }
            }
            mouseX = e.getX(); mouseY = e.getY();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (objetoAtual != null && objetoAtual.sendoArrastado) {
                objetoAtual.sendoArrastado = false;
                
                double origemX = estilingueX + 35;
                double origemY = CHAO_Y - 50;
                double puxadoX = objetoAtual.x + 35;
                double puxadoY = objetoAtual.y + 35;
                
                double angulo = Math.atan2(origemY - puxadoY, origemX - puxadoX);
                double dist = Point.distance(origemX, origemY, puxadoX, puxadoY);
                double forca = Math.min(30, dist / 4.5); 
                
                if (forca > 5) {
                    objetoAtual.lancar(angulo, forca);
                    tocarSom("audios/arremesso.wav"); // CORRIGIDO PARA .WAV
                } else {
                    objetoAtual.x = estilingueX + 2;
                    objetoAtual.y = CHAO_Y - 80;
                }
            }
        }

        @Override public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
        @Override public void mouseClicked(MouseEvent e) {}
        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) {}

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            desenharCenarioProcedural(g2);
            desenharPainelPontuacao(g2);

            for (Lixeira l : lixeiras) l.desenhar(g2);
            desenharEstilingue(g2);
            if (objetoAtual != null) objetoAtual.desenhar(g2);
            
            desenharAvisos(g2);
        }

        private void desenharCenarioProcedural(Graphics2D g2) {
            GradientPaint gradienteCeu = new GradientPaint(0, 0, new Color(135, 206, 250), 0, CHAO_Y, new Color(240, 248, 255));
            g2.setPaint(gradienteCeu);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(255, 255, 255, 200));
            desenharNuvem(g2, 100, 100, 1.2);
            desenharNuvem(g2, 400, 150, 0.8);
            desenharNuvem(g2, 750, 80, 1.0);

            GradientPaint gradienteGrama = new GradientPaint(0, CHAO_Y, new Color(34, 139, 34), 0, 750, new Color(50, 205, 50));
            g2.setPaint(gradienteGrama);
            g2.fillRect(0, CHAO_Y, getWidth(), 300);
            
            g2.setColor(new Color(0, 100, 0, 30));
            for(int i=0; i<getWidth(); i+=10) g2.drawLine(i, CHAO_Y, i + random.nextInt(5), 750);
        }

        private void desenharNuvem(Graphics2D g2, int x, int y, double escala) {
            int b = (int)(30 * escala);
            g2.fillOval(x, y, (int)(60*escala), (int)(60*escala));
            g2.fillOval(x+b, y-b/2, (int)(70*escala), (int)(70*escala));
            g2.fillOval(x+b*2, y, (int)(60*escala), (int)(60*escala));
        }

        private void desenharPainelPontuacao(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 150)); 
            g2.fillRoundRect(20, 20, 250, 160, 18, 18);

            g2.setColor(Color.WHITE);
            g2.setFont(fonteHUD);
            g2.drawString("🌟 PONTOS: " + pontos, 35, 50);
            g2.drawString("❤️ VIDAS: " + vidas, 35, 80);
            g2.setColor(Color.YELLOW);
            g2.drawString("🚀 FASE: " + fase, 35, 110);
            if (combo >= 2) {
                g2.setColor(Color.ORANGE);
                g2.drawString("🔥 COMBO " + combo + "x", 35, 140);
            }

            if (fase >= 2) {
                long tempoGasto = System.currentTimeMillis() - inicioRodada;
                int larguraBarra = (int) (210 * (1.0 - (double) tempoGasto / tempoLimite));
                g2.setColor(Color.BLACK);
                g2.fillRect(40, 160, 210, 10);
                g2.setColor(larguraBarra > 50 ? Color.GREEN : Color.RED);
                g2.fillRect(40, 160, Math.max(0, larguraBarra), 10);
            }
        }

        private void desenharEstilingue(Graphics2D g2) {
            int x = estilingueX;
            int y = CHAO_Y;
            Color corMadeira = new Color(100, 50, 10);
            
            g2.setStroke(new BasicStroke(15, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(corMadeira);
            g2.drawLine(x, y - 50, x - 30, y - 100); 
            g2.drawLine(x, y - 50, x + 30, y - 100); 
            g2.setStroke(new BasicStroke(20, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x, y - 50, x, y + 20);
            
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillOval(x - 20, y + 10, 40, 15);

            if (objetoAtual != null && !objetoAtual.voando) {
                g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(20, 20, 20)); 
                
                int pEsquerdoX = x - 28, pEsquerdoY = y - 95;
                int pDireitoX = x + 28, pDireitoY = y - 95;
                int lixoX = (int)objetoAtual.x + 35, lixoY = (int)objetoAtual.y + 35;
                
                g2.drawLine(pEsquerdoX, pEsquerdoY, lixoX, lixoY);
                g2.drawLine(pDireitoX, pDireitoY, lixoX, lixoY);
                
                if (objetoAtual.sendoArrastado) {
                    desenharTrajetoria(g2, estilingueX + 35, CHAO_Y - 50, lixoX, lixoY);
                }
            }
        }
        
        private void desenharTrajetoria(Graphics2D g2, int origemX, int origemY, int puxadoX, int puxadoY) {
            if (tamanhoMira <= 0) return;

            g2.setColor(new Color(0, 0, 0, 200)); 
            g2.setStroke(new BasicStroke(4));
            
            double angulo = Math.atan2(origemY - puxadoY, origemX - puxadoX);
            double dist = Point.distance(origemX, origemY, puxadoX, puxadoY);
            double forca = Math.min(30, dist / 4.5);
            
            double simX = puxadoX;
            double simY = puxadoY;
            double simVx = Math.cos(angulo) * forca;
            double simVy = Math.sin(angulo) * forca;
            
            for(int i = 0; i < tamanhoMira; i++) {
                simX += simVx;
                simY += simVy;
                simVy += 0.45; 
                
                if(i % 4 == 0) g2.fillOval((int)simX - 4, (int)simY - 4, 8, 8);
            }
        }

        private void desenharAvisos(Graphics2D g2) {
            if (System.currentTimeMillis() < avisoAte) {
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRoundRect(290, 285, 400, 80, 18, 18);
                g2.setColor(Color.WHITE);
                g2.setFont(fonteAviso);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(aviso, 490 - fm.stringWidth(aviso) / 2, 335);
            }
        }
    }

    class ItemData {
        String tipo, caminho;
        ItemData(String tipo, String caminho) { this.tipo = tipo; this.caminho = caminho; }
    }

    class Lixeira {
        int x, y, w, h;
        String tipo;
        Color cor;

        Lixeira(int x, int y, int w, int h, String tipo, Color cor) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.tipo = tipo; this.cor = cor;
        }

        Rectangle getHitboxTop() { return new Rectangle(x + 10, y - 10, w - 20, 30); }

        void desenhar(Graphics2D g2) {
            GradientPaint gradienteCorpo = new GradientPaint(x, y, cor, x + w, y, cor.darker());
            g2.setPaint(gradienteCorpo);
            g2.fillRoundRect(x, y, w, h, 15, 15);
            
            g2.setColor(new Color(240, 240, 240));
            g2.fillRoundRect(x + 10, y - 10, w - 20, 20, 10, 10);
            
            g2.setColor(new Color(30, 30, 30));
            g2.fillRoundRect(x + 25, y - 5, w - 50, 10, 5, 5);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(tipo, x + (w - fm.stringWidth(tipo)) / 2, y + h/2 + 5);
        }
    }

    class Objeto {
        double x, y, vx, vy;
        int largura = 70, altura = 70;
        String tipo;
        BufferedImage imagem;
        boolean voando = false;
        boolean sendoArrastado = false;

        Objeto(String tipo, String caminho, int nascX, int nascY) {
            this.tipo = tipo;
            this.x = nascX - largura/2; 
            this.y = nascY - altura/2;
            
            carregarImagemSegura(caminho);
        }

        private void carregarImagemSegura(String caminho) {
            String[] possibilidades = { caminho, "../" + caminho, "java/01/" + caminho, "01/" + caminho };
            for (String p : possibilidades) {
                File arquivo = new File(p);
                if (arquivo.exists()) {
                    try { imagem = ImageIO.read(arquivo); return; } catch (IOException e) {}
                }
            }
        }

        void lancar(double angulo, double forca) {
            vx = Math.cos(angulo) * forca;
            vy = Math.sin(angulo) * forca; 
            voando = true;
        }

        void atualizar() {
            if (sendoArrastado || !voando) return; 
            
            x += vx;
            y += vy;
            vy += 0.45; // Gravidade
        }

        void desenhar(Graphics2D g2) {
            if (imagem != null) {
                g2.drawImage(imagem, (int)x, (int)y, largura, altura, null);
            } else {
                g2.setColor(Color.WHITE);
                g2.fillOval((int)x, (int)y, largura, altura);
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial", Font.BOLD, 10));
                g2.drawString(tipo.substring(0, Math.min(4, tipo.length())), (int)x + 15, (int)y + 40);
            }
        }

        Rectangle getBounds() { return new Rectangle((int)x + 10, (int)y + 10, largura - 20, altura - 20); }
    }
}