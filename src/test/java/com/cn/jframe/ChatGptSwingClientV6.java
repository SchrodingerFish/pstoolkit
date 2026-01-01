package com.cn.jframe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * ChatGPT Swing Client V6
 * 升级：使用 commonmark-java 进行专业级 Markdown 解析。
 * 支持：表格、列表、引用、标准代码块渲染。
 */
public class ChatGptSwingClientV6 extends JFrame {

    private static final String FONT_FACE = "Maple Mono NF CN";
    private static final int FONT_SIZE = 18;

    private static final Color BG_COLOR = new Color(245, 245, 247);
    private static final Color TOP_BAR_COLOR = new Color(255, 255, 255);
    private static final Color USER_BUBBLE_COLOR = new Color(0, 122, 255);
    private static final Color AI_BUBBLE_COLOR = new Color(255, 255, 255);

    private JPanel chatListPanel;
    private JScrollPane scrollPane;
    private JTextField inputField;
    private JButton sendButton;
    private JButton clearButton;
    private JLabel statusLabel;

    private final List<Message> conversationHistory = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HttpClient httpClient;
    private final AppConfig config = new AppConfig();

    // Markdown 解析器实例 (复用)
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();

    private volatile boolean isTyping = false;
    private Timer typewriterTimer;
    private BubblePanel currentAiBubble;
    private String currentAiFullText = "";
    private int typeIndex = 0;

    public ChatGptSwingClientV6() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        config.load();

        initUI();
    }

    private void initUI() {
        setTitle("ChatGPT Professional Client V6");
        setSize(1100, 850);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_COLOR);
        setContentPane(mainPanel);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(TOP_BAR_COLOR);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
        headerPanel.setPreferredSize(new Dimension(getWidth(), 60));

        JLabel titleLabel = new JLabel(" AI Assistant", JLabel.LEFT);
        titleLabel.setFont(new Font(FONT_FACE, Font.BOLD, 22));
        titleLabel.setBorder(new EmptyBorder(0, 20, 0, 0));

        JButton settingsButton = new JButton("⚙ 设置");
        styleButton(settingsButton, new Color(245, 245, 245), Color.BLACK);
        settingsButton.addActionListener(e -> showSettingsDialog());

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        headerRight.setOpaque(false);
        headerRight.add(settingsButton);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(headerRight, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Chat Area
        chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        chatListPanel.setBackground(BG_COLOR);
        chatListPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        scrollPane = new JScrollPane(chatListPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Area
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBackground(TOP_BAR_COLOR);
        bottomPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        clearButton = new JButton("🗑");
        clearButton.setToolTipText("清空历史");
        styleButton(clearButton, new Color(255, 240, 240), Color.RED);
        clearButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        clearButton.setPreferredSize(new Dimension(50, 45));
        clearButton.addActionListener(e -> clearHistory());

        inputField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isOpaque() && getBorder() instanceof RoundBorder) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        inputField.setOpaque(false);
        inputField.setBorder(new RoundBorder(new Color(200, 200, 200), 10));
        inputField.setFont(new Font(FONT_FACE, Font.PLAIN, FONT_SIZE));
        inputField.setBackground(new Color(245, 245, 245));
        inputField.addActionListener(e -> onSend());

        sendButton = new JButton("发送");
        styleButton(sendButton, USER_BUBBLE_COLOR, Color.WHITE);
        sendButton.setPreferredSize(new Dimension(90, 45));
        sendButton.addActionListener(e -> onSend());

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font(FONT_FACE, Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        bottomPanel.add(clearButton, BorderLayout.WEST);
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // --- Markdown 解析器封装 (使用 commonmark-java) ---
    static class MarkdownRenderer {
        private final Parser parser;
        private final HtmlRenderer renderer;

        public MarkdownRenderer() {
            // 启用表格扩展
            List<Extension> extensions = Arrays.asList(TablesExtension.create());
            this.parser = Parser.builder().extensions(extensions).build();
            this.renderer = HtmlRenderer.builder().extensions(extensions).build();
        }

        public String render(String markdown) {
            if (markdown == null) return "";
            Node document = parser.parse(markdown);
            return renderer.render(document); // 转为标准 HTML
        }
    }

    private void onSend() {
        if (isTyping) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        if (config.getApiKey().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先设置 API Key");
            return;
        }

        inputField.setText("");
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        clearButton.setEnabled(false);
        statusLabel.setText("Thinking...");

        addBubble(text, true);
        conversationHistory.add(new Message("user", text));

        currentAiBubble = addBubble("...", false);

        ChatRequest payload = new ChatRequest(config.getModel(), conversationHistory);
        String jsonBody = gson.toJson(payload);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getApiUrl()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    ChatResponse chatResp = gson.fromJson(response.body(), ChatResponse.class);
                    if (chatResp.choices() != null && !chatResp.choices().isEmpty()) {
                        String content = chatResp.choices().get(0).message().content();
                        SwingUtilities.invokeLater(() -> startTypewriter(content));
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        currentAiBubble.setTextContent("Error: " + response.statusCode());
                        resetState();
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    currentAiBubble.setTextContent("Error: " + e.getMessage());
                    resetState();
                });
                e.printStackTrace();
            }
        });
    }

    private BubblePanel addBubble(String text, boolean isUser) {
        JPanel wrapper = new JPanel(new FlowLayout(isUser ? FlowLayout.RIGHT : FlowLayout.LEFT));
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(5, 0, 5, 0));

        AvatarPanel avatar = new AvatarPanel(isUser ? "Me" : "AI", isUser ? USER_BUBBLE_COLOR : new Color(90, 90, 90));
        // 注意：这里传入的是 markdownRenderer
        BubblePanel bubble = new BubblePanel(text, isUser, markdownRenderer);

        if (isUser) {
            wrapper.add(bubble);
            wrapper.add(avatar);
        } else {
            wrapper.add(avatar);
            wrapper.add(bubble);
        }

        chatListPanel.add(wrapper);
        chatListPanel.revalidate();
        scrollToBottom();
        return bubble;
    }

    private void startTypewriter(String fullText) {
        isTyping = true;
        currentAiFullText = fullText;
        typeIndex = 0;
        statusLabel.setText("Typing...");

        conversationHistory.add(new Message("assistant", fullText));

        typewriterTimer = new Timer(20, e -> {
            // 打字机逻辑：为了防止 commonmark 解析不完整的 markdown 标签（如 <ta...），
            // 简单的做法是：仅在特定字符后渲染，或者接受轻微的闪烁。
            // 这里我们采用全量截取渲染。Commonmark 容错性很好。
            typeIndex += 5; // 加快一点速度，因为大文本渲染慢
            if (typeIndex > currentAiFullText.length()) typeIndex = currentAiFullText.length();

            String sub = currentAiFullText.substring(0, typeIndex);
            currentAiBubble.setTextContent(sub);
            scrollToBottom();

            if (typeIndex >= currentAiFullText.length()) {
                ((Timer)e.getSource()).stop();
                resetState();
            }
        });
        typewriterTimer.start();
    }

    private void resetState() {
        isTyping = false;
        inputField.setEnabled(true);
        sendButton.setEnabled(true);
        clearButton.setEnabled(true);
        inputField.requestFocus();
        statusLabel.setText("Ready");
    }

    private void clearHistory() {
        if (isTyping) return;
        int opt = JOptionPane.showConfirmDialog(this, "清空所有对话记录？", "确认", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            conversationHistory.clear();
            chatListPanel.removeAll();
            chatListPanel.revalidate();
            chatListPanel.repaint();
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    // --- 气泡组件 (CSS 重点优化) ---
    static class BubblePanel extends JPanel {
        private final boolean isUser;
        private final JEditorPane textPane;
        private final MarkdownRenderer renderer;
        private static final int MAX_WIDTH = 780;

        public BubblePanel(String text, boolean isUser, MarkdownRenderer renderer) {
            this.isUser = isUser;
            this.renderer = renderer;
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(12, 18, 12, 18));

            textPane = new JEditorPane();
            textPane.setEditable(false);
            textPane.setContentType("text/html");
            textPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            textPane.setOpaque(false);

            // --- 核心 CSS 样式表 (适配 Swing HTML 3.2 引擎) ---
            HTMLEditorKit kit = new HTMLEditorKit();
            StyleSheet style = kit.getStyleSheet();

            String fontFamily = String.format("'%s', 'Microsoft YaHei', sans-serif", FONT_FACE);
            String textColor = isUser ? "white" : "#333333";
            String codeBg = isUser ? "#1E90FF" : "#F2F2F2";
            String quoteBorder = isUser ? "white" : "#AAAAAA";

            // 全局
            style.addRule("body { font-family: " + fontFamily + "; font-size: " + FONT_SIZE + "px; color: " + textColor + "; margin: 0; }");

            // 段落与标题
            style.addRule("p { margin: 6px 0; }");
            style.addRule("h1, h2, h3, h4, h5 { margin: 10px 0 5px 0; font-weight: bold; }");
            style.addRule("h1 { font-size: " + (FONT_SIZE + 6) + "px; }");
            style.addRule("h2 { font-size: " + (FONT_SIZE + 4) + "px; }");

            // 列表
            style.addRule("ul { margin-left: 20px; margin-top: 5px; margin-bottom: 5px; }");
            style.addRule("ol { margin-left: 20px; margin-top: 5px; margin-bottom: 5px; }");
            style.addRule("li { margin: 2px 0; }");

            // 代码块 (Pre & Code)
            // 注意：Swing 对 pre 的 border 支持一般，通常用背景色区分
            style.addRule("pre { font-family: '" + FONT_FACE + "'; background-color: " + codeBg + "; padding: 8px; margin: 8px 0; }");
            style.addRule("code { font-family: '" + FONT_FACE + "'; font-weight: bold; }"); // 行内代码

            // 引用块 (Blockquote) - 模拟左边框
            // Swing HTML 不支持 border-left，所以我们用 margin + 背景色或者嵌套 table 模拟，这里用简单缩进
            style.addRule("blockquote { margin-left: 10px; padding-left: 10px; color: #666666; background-color: #f9f9f9; }");
            if (isUser) {
                style.addRule("blockquote { color: #dddddd; background-color: transparent; }");
            }

            // 表格 (Table) - CommonMark 生成的是 standard HTML table
            // 需要设置 border=1 才能在 Swing 中看到边框
            style.addRule("table { border-collapse: collapse; margin: 8px 0; width: 100%; }");
            style.addRule("th { font-weight: bold; background-color: #E0E0E0; padding: 4px; text-align: left; color: #000000;}");
            style.addRule("td { padding: 4px; background-color: #FAFAFA; color: #000000; }");

            textPane.setEditorKit(kit);
            setTextContent(text);

            add(textPane, BorderLayout.CENTER);
        }

        public void setTextContent(String text) {
            // 1. 使用 CommonMark 解析 Markdown -> HTML
            String htmlBody = renderer.render(text);

            // 2. 针对 Swing 表格的 Hack：Swing 默认不显示 table border，除非属性里写了 border="1"
            //    Commonmark 生成的 html 不带 border 属性。我们暴力替换一下。
            htmlBody = htmlBody.replace("<table>", "<table border='1' cellspacing='0' cellpadding='4'>");

            // 3. 包装完整 HTML
            textPane.setText("<html><body>" + htmlBody + "</body></html>");

            // 4. 计算尺寸
            setSize(new Dimension(MAX_WIDTH, Short.MAX_VALUE));
            Dimension pref = textPane.getPreferredSize();
            int w = Math.min(MAX_WIDTH, pref.width + 30);
            int h = pref.height + 24;
            setPreferredSize(new Dimension(w, h));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isUser ? USER_BUBBLE_COLOR : AI_BUBBLE_COLOR);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            if (!isUser) {
                g2.setColor(new Color(220, 220, 220));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 24, 24);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // --- 辅助类 ---
    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font(FONT_FACE, Font.BOLD, 16));
        btn.setBorder(new EmptyBorder(5, 12, 5, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    static class AvatarPanel extends JPanel {
        private final String letter;
        private final Color color;
        public AvatarPanel(String letter, Color color) {
            this.letter = letter;
            this.color = color;
            setPreferredSize(new Dimension(42, 42));
            setOpaque(false);
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, 42, 42);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(FONT_FACE, Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            int x = (42 - fm.stringWidth(letter)) / 2;
            int y = ((42 - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(letter, x, y);
            g2.dispose();
        }
    }

    static class RoundBorder implements javax.swing.border.Border {
        private final Color color;
        private final int radius;
        public RoundBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        public Insets getBorderInsets(Component c) { return new Insets(radius+5, radius+5, radius+5, radius+5); }
        public boolean isBorderOpaque() { return true; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }
    }

    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField urlField = new JTextField(config.getApiUrl(), 25);
        JTextField keyField = new JPasswordField(config.getApiKey(), 25);
        JTextField modelField = new JTextField(config.getModel(), 25);
        Font f = new Font(FONT_FACE, Font.PLAIN, 14);
        urlField.setFont(f); keyField.setFont(f); modelField.setFont(f);

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("API URL:"), gbc);
        gbc.gridx = 1; dialog.add(urlField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; dialog.add(keyField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1; dialog.add(modelField, gbc);

        JButton saveBtn = new JButton("Save");
        saveBtn.setFont(new Font(FONT_FACE, Font.BOLD, 14));
        saveBtn.addActionListener(e -> {
            config.setApiUrl(urlField.getText().trim());
            config.setApiKey(keyField.getText().trim());
            config.setModel(modelField.getText().trim());
            config.save();
            dialog.dispose();
        });
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        dialog.add(saveBtn, gbc);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    static class AppConfig {
        private final Preferences prefs = Preferences.userNodeForPackage(ChatGptSwingClientV6.class);
        private String apiUrl, apiKey, model;
        public void load() {
            apiUrl = prefs.get("api_url", "https://api.openai.com/v1/chat/completions");
            apiKey = prefs.get("api_key", "");
            model = prefs.get("model", "gpt-3.5-turbo");
        }
        public void save() {
            prefs.put("api_url", apiUrl);
            prefs.put("api_key", apiKey);
            prefs.put("model", model);
        }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String s) { apiUrl = s; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String s) { apiKey = s; }
        public String getModel() { return model; }
        public void setModel(String s) { model = s; }
    }

    record ChatRequest(String model, List<Message> messages) {}
    record Message(String role, String content) {}
    record ChatResponse(String id, List<Choice> choices) {}
    record Choice(int index, Message message) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatGptSwingClientV6().setVisible(true));
    }
}