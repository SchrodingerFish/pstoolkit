package com.cn.jframe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ChatGPT Swing Client V3 (UI Beautified)
 * 特性：气泡UI、圆角设计、自定义绘制、头像、Markdown、JDK 25
 */
public class ChatGptSwingClientV3 extends JFrame {

    // --- UI 配色方案 ---
    private static final Color BG_COLOR = new Color(245, 245, 247); // 整体背景灰
    private static final Color TOP_BAR_COLOR = new Color(255, 255, 255);
    private static final Color USER_BUBBLE_COLOR = new Color(0, 122, 255); // iOS Blue
    private static final Color AI_BUBBLE_COLOR = new Color(255, 255, 255); // White
    private static final Color TEXT_COLOR_USER = Color.WHITE;
    private static final Color TEXT_COLOR_AI = new Color(30, 30, 30);
    private static final Font MAIN_FONT = new Font("Maple Mono NF CN", Font.PLAIN, 17);

    // --- 组件 ---
    private JPanel chatListPanel; // 垂直存放气泡的容器
    private JScrollPane scrollPane;
    private JTextField inputField;
    private JButton sendButton;
    private JButton settingsButton;
    private JLabel statusLabel;

    // --- 数据 ---
    private final List<Message> conversationHistory = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HttpClient httpClient;
    private final AppConfig config = new AppConfig();

    // --- 打字机状态 ---
    private volatile boolean isTyping = false;
    private Timer typewriterTimer;
    private BubblePanel currentAiBubble; // 当前正在打字的那个气泡引用
    private String currentAiFullText = "";
    private int typeIndex = 0;

    public ChatGptSwingClientV3() {
        // 配置全局字体抗锯齿
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
        setTitle("ChatGPT Modern Client");
        setSize(950, 750);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 主容器
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_COLOR);
        setContentPane(mainPanel);

        // 1. 顶部栏 (Header)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(TOP_BAR_COLOR);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
        headerPanel.setPreferredSize(new Dimension(getWidth(), 50));

        JLabel titleLabel = new JLabel(" ChatGPT Assistant", JLabel.LEFT);
        titleLabel.setFont(new Font("Maple Mono NF CN", Font.BOLD, 16));
        titleLabel.setBorder(new EmptyBorder(0, 15, 0, 0));

        settingsButton = new JButton("⚙ Settings");
        styleButton(settingsButton, new Color(240, 240, 240), Color.BLACK);
        settingsButton.addActionListener(e -> showSettingsDialog());

        headerPanel.add(titleLabel, BorderLayout.WEST);
        JPanel btnContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnContainer.setOpaque(false);
        btnContainer.add(settingsButton);
        headerPanel.add(btnContainer, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // 2. 聊天列表区域 (Center)
        chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        chatListPanel.setBackground(BG_COLOR);
        chatListPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 只有垂直滚动，并且加快滚动速度
        scrollPane = new JScrollPane(chatListPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // 3. 底部输入区 (Bottom)
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBackground(TOP_BAR_COLOR);
        bottomPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 圆角输入框包装
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
        inputField.setBorder(new RoundBorder(new Color(200, 200, 200), 10)); // 圆角边框
        inputField.setFont(MAIN_FONT);
        inputField.setBackground(new Color(245, 245, 245));
        inputField.addActionListener(e -> onSend());

        sendButton = new JButton("Send");
        styleButton(sendButton, USER_BUBBLE_COLOR, Color.WHITE);
        sendButton.setPreferredSize(new Dimension(80, 35));
        sendButton.addActionListener(e -> onSend());

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Maple Mono NF CN", Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // 辅助方法：按钮样式
    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Maple Mono NF CN", Font.BOLD, 14));
        btn.setBorder(new EmptyBorder(5, 15, 5, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // 简单的 Hover 效果
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setOpaque(true); }
            public void mouseExited(MouseEvent e) { btn.setOpaque(true); }
        });
    }

    private void onSend() {
        if (isTyping) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        if (config.getApiKey().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请点击右上角 Settings 设置 API Key");
            return;
        }

        inputField.setText("");
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        statusLabel.setText("Thinking...");

        // 1. 添加用户气泡
        addBubble(text, true);
        conversationHistory.add(new Message("user", text));

        // 2. 准备 AI 气泡（先显示空的或加载中）
        currentAiBubble = addBubble("...", false);

        // 3. 异步请求
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
                    currentAiBubble.setTextContent("Exception: " + e.getMessage());
                    resetState();
                });
                e.printStackTrace();
            }
        });
    }

    // --- 核心UI：添加气泡 ---
    private BubblePanel addBubble(String text, boolean isUser) {
        JPanel wrapper = new JPanel(new FlowLayout(isUser ? FlowLayout.RIGHT : FlowLayout.LEFT));
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(5, 0, 5, 0));

        // 头像
        AvatarPanel avatar = new AvatarPanel(isUser ? "U" : "AI", isUser ? USER_BUBBLE_COLOR : new Color(100, 100, 100));

        BubblePanel bubble = new BubblePanel(text, isUser);

        if (isUser) {
            wrapper.add(bubble);
            wrapper.add(avatar);
        } else {
            wrapper.add(avatar);
            wrapper.add(bubble);
        }

        chatListPanel.add(wrapper);
        chatListPanel.revalidate(); // 重新布局
        scrollToBottom();
        return bubble;
    }

    private void startTypewriter(String fullText) {
        isTyping = true;
        currentAiFullText = fullText;
        typeIndex = 0;
        statusLabel.setText("Typing...");

        // 将完整消息加入历史
        conversationHistory.add(new Message("assistant", fullText));

        typewriterTimer = new Timer(20, e -> {
            typeIndex += 3; // 速度
            if (typeIndex > currentAiFullText.length()) typeIndex = currentAiFullText.length();

            String sub = currentAiFullText.substring(0, typeIndex);
            currentAiBubble.setTextContent(sub);

            // 自动滚动
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
        inputField.requestFocus();
        statusLabel.setText("Ready");
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    // --- 组件：自定义气泡面板 ---
    static class BubblePanel extends JPanel {
        private final boolean isUser;
        private final JEditorPane textPane;
        private static final int MAX_WIDTH = 600; // 气泡最大宽度

        public BubblePanel(String text, boolean isUser) {
            this.isUser = isUser;
            setLayout(new BorderLayout());
            setOpaque(false); // 背景透明，由 paintComponent 绘制圆角矩形
            setBorder(new EmptyBorder(10, 15, 10, 15)); // 内边距

            textPane = new JEditorPane();
            textPane.setEditable(false);
            textPane.setContentType("text/html");
            textPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            textPane.setOpaque(false); // 透明，透出 BubblePanel 的绘制

            // 设置 CSS
            HTMLEditorKit kit = new HTMLEditorKit();
            StyleSheet style = kit.getStyleSheet();
            style.addRule("body { font-family: 'Maple Mono NF CN', sans-serif; font-size: 14px; margin: 0; }");
            // 用户字体白色，AI字体深色
            String textColor = isUser ? "white" : "#333333";
            String codeBg = isUser ? "#1E90FF" : "#F0F0F0"; // 用户气泡里的代码块稍微亮一点
            style.addRule("body { color: " + textColor + "; }");
            style.addRule("p { margin: 2px 0; }");
            style.addRule("code { font-family: Maple Mono NF CN; background-color: " + codeBg + "; padding: 2px; border-radius: 3px;}");
            style.addRule("pre { background-color: " + codeBg + "; padding: 5px; border-radius: 5px; overflow: auto; margin: 5px 0;}");

            textPane.setEditorKit(kit);
            setTextContent(text);

            add(textPane, BorderLayout.CENTER);
        }

        public void setTextContent(String text) {
            String html = SimpleMarkdownParser.parse(text);
            textPane.setText("<html><body>" + html + "</body></html>");

            // 动态计算大小
            setSize(new Dimension(MAX_WIDTH, Short.MAX_VALUE));
            Dimension pref = textPane.getPreferredSize();
            // 限制宽度
            int w = Math.min(MAX_WIDTH, pref.width + 30);
            int h = pref.height + 20;
            setPreferredSize(new Dimension(w, h));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(isUser ? USER_BUBBLE_COLOR : AI_BUBBLE_COLOR);
            // 绘制圆角矩形背景
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

            // 如果是 AI，加一个淡淡的边框/阴影感
            if (!isUser) {
                g2.setColor(new Color(220, 220, 220));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // --- 组件：圆形头像 ---
    static class AvatarPanel extends JPanel {
        private final String letter;
        private final Color color;

        public AvatarPanel(String letter, Color color) {
            this.letter = letter;
            this.color = color;
            setPreferredSize(new Dimension(36, 36));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 画圆
            g2.setColor(color);
            g2.fillOval(0, 0, 36, 36);

            // 画字
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Maple Mono NF CN", Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            int x = (36 - fm.stringWidth(letter)) / 2;
            int y = ((36 - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(letter, x, y);

            g2.dispose();
        }
    }

    // --- 组件：圆角边框 ---
    static class RoundBorder implements javax.swing.border.Border {
        private final Color color;
        private final int radius;
        public RoundBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        public Insets getBorderInsets(Component c) { return new Insets(this.radius+1, this.radius+1, this.radius+2, this.radius); }
        public boolean isBorderOpaque() { return true; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }
    }

    // --- 配置与工具类 (复用) ---
    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField urlField = new JTextField(config.getApiUrl(), 25);
        JTextField keyField = new JPasswordField(config.getApiKey(), 25);
        JTextField modelField = new JTextField(config.getModel(), 25);

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("API URL:"), gbc);
        gbc.gridx = 1; dialog.add(urlField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; dialog.add(keyField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1; dialog.add(modelField, gbc);

        JButton saveBtn = new JButton("Save Configuration");
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

    static class SimpleMarkdownParser {
        public static String parse(String markdown) {
            if (markdown == null) return "";
            String html = markdown.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            html = html.replaceAll("```(.*?)```", "<pre>$1</pre>");
            html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
            html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
            html = html.replace("\n", "<br>");
            return html;
        }
    }

    static class AppConfig {
        private final Preferences prefs = Preferences.userNodeForPackage(ChatGptSwingClientV3.class);
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
        SwingUtilities.invokeLater(() -> new ChatGptSwingClientV3().setVisible(true));
    }
}