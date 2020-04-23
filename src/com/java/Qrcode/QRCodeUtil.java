package com.java.Qrcode;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.print.*;
import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.HashDocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.*;
import javax.servlet.http.HttpServletResponse;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import javax.swing.JFrame;

public final class QRCodeUtil extends LuminanceSource {
    private static final Logger logger = LoggerFactory.getLogger(QRCodeUtil.class);
    //二维码颜色
    private static final int BLACK = 0xFF000000;
    //二维码颜色
    private static final int WHITE = 0xFFFFFFFF;

    private static final int LogoPart = 4;

    private final BufferedImage image;
    private final int left;
    private final int top;

    public QRCodeUtil(BufferedImage image) {
        this(image, 0, 0, image.getWidth(), image.getHeight());
    }

    public QRCodeUtil(BufferedImage image, int left, int top, int width, int height) {
        super(width, height);
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        if (left + width > sourceWidth || top + height > sourceHeight) {
            throw new IllegalArgumentException("Crop rectangle dose not fit within image data.");
        }
        for (int y = top; y < top + height; y++) {
            for (int x = left; x < left + width; x++) {
                if ((image.getRGB(x, y) & 0xFF000000) == 0) {
                    image.setRGB(x, y, 0xFFFFFFFF); //=white
                }
            }
        }

        this.image = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_BYTE_GRAY);
        this.image.getGraphics().drawImage(image, 0, 0, null);
        this.left = left;
        this.top = top;

    }

    @Override
    public byte[] getRow(int y, byte[] row) {
        if (y < 0 || y >= getHeight()) {
            throw new IllegalArgumentException("Requested row is outside the image:" + y);
        }
        int width = getWidth();
        if (row == null || row.length < width) {
            row = new byte[width];
        }
        image.getRaster().getDataElements(left, top + y, width, 1, row);
        return row;
    }

    @Override
    public byte[] getMatrix() {
        int width = getWidth();
        int height = getHeight();
        int area = width * height;
        byte[] matrix = new byte[area];
        image.getRaster().getDataElements(left, top, width, height, matrix);
        return matrix;
    }

    @Override
    public boolean isCropSupported() {
        return true;
    }

    @Override
    public LuminanceSource crop(int left, int top, int width, int height) {
        return new QRCodeUtil(image, this.left + left, this.top + top, width, height);
    }

    @Override
    public boolean isRotateSupported() {
        return true;
    }

    @Override
    public LuminanceSource rotateCounterClockwise() {
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        AffineTransform transform = new AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, sourceWidth);
        BufferedImage rotatedImage = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = rotatedImage.createGraphics();
        g.drawImage(image, transform, null);
        g.dispose();
        int width = getWidth();
        return new QRCodeUtil(rotatedImage, top, sourceWidth - (left + width), getHeight(), width);
    }

    private static BufferedImage toBufferedImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
        }
        return image;
    }

    /**
     * 生成二维码图片
     */

    public static void writeToFile(BitMatrix matrix, String format, File file) throws IOException {
        BufferedImage image = toBufferedImage(matrix);
        if (!ImageIO.write(image, format, file)) {
            throw new IOException("Could not write an image of format" + format + "to" + file);
        }
    }

    /**
     * 生成二维码图片流
     */

    public static void writeToStream(BitMatrix matrix, String format, OutputStream stream) throws IOException {
        BufferedImage image = toBufferedImage(matrix);
        if (!ImageIO.write(image, format, stream)) {
            throw new IOException("Could not write an image of format" + format);
        }
    }

    /**
     * 根据内容生成指定格式的二维码图片   String pathName
     */
    private static String generateQRCode(String text, int width, int height, String format, String pathName) throws Exception {
        Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);//指定纠错等级
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        File outputFile = new File(pathName);
        writeToFile(bitMatrix, format, outputFile);

        return pathName;
    }

    /**
     * 输出二维码图片流
     */

    public static void generateQRCode(String text, int width, int height, String format, HttpServletResponse response) throws Exception {
        Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        writeToStream(bitMatrix, format, response.getOutputStream());
    }


    /**
     * 解析指定路径下的二维码
     */
    public static String parseQRCode(String filePath) {
        String content = "";
        try {
            File file = new File(filePath);
            BufferedImage image = ImageIO.read(file);
            LuminanceSource source = new QRCodeUtil(image);
            Binarizer binarizer = new HybridBinarizer(source);
            BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);
            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            MultiFormatReader formatReader = new MultiFormatReader();
            Result result = formatReader.decode(binaryBitmap, hints);

            //logger.info("reault 为：" + result.toString());
            //logger.info("resultFormat为：" + result.getBarcodeFormat());
            //logger.info("resultText为：" + result.getText());

            //设置返回值
            content = result.getText();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return content;
    }

    /**
     * 打印二维码
     */

    public static void drawImage(String fileName, int count) {
        try {
            DocFlavor dof = null;

            if (fileName.endsWith(".gif")) {
                dof = DocFlavor.INPUT_STREAM.GIF;
            } else if (fileName.endsWith(".jpg")) {
                dof = DocFlavor.INPUT_STREAM.JPEG;
            } else if (fileName.endsWith(".png")) {
                dof = DocFlavor.INPUT_STREAM.PNG;
            }
            //获取默认打印机
            PrintService ps = PrintServiceLookup.lookupDefaultPrintService();

            PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
            pras.add(new Copies(count));
            pras.add(MediaSizeName.ISO_A10);//设置打印的纸张

            DocAttributeSet das = new HashDocAttributeSet();
            das.add(new MediaPrintableArea(0, 0, 1, 1, MediaPrintableArea.INCH));
            FileInputStream fin = new FileInputStream(fileName);

            Doc doc = new SimpleDoc(fin, dof, das);
            DocPrintJob job = ps.createPrintJob();
            job.print(doc, pras);
            fin.close();

        } catch (FileNotFoundException | PrintException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 打印图片的实现
     */
    public static void printFileAction(String fileName, int count) {
        FileInputStream fin = null;
        try {
            DocFlavor dof = null;
            if (fileName.endsWith(".gif")) {
                dof = DocFlavor.INPUT_STREAM.GIF;
            } else if (fileName.endsWith("jpg")) {
                dof = DocFlavor.INPUT_STREAM.JPEG;
            } else if (fileName.endsWith("png")) {
                dof = DocFlavor.INPUT_STREAM.PNG;
            }

            PrintService ps = PrintServiceLookup.lookupDefaultPrintService();
            PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
            pras.add(OrientationRequested.PORTRAIT);
            pras.add(new Copies(count));
            pras.add(PrintQuality.HIGH);
            DocAttributeSet das = new HashDocAttributeSet();

            //设置打印纸张的大小
            das.add(new MediaPrintableArea(0, 0, 300, 296, MediaPrintableArea.MM));
            fin = new FileInputStream(fileName);
            Doc doc = new SimpleDoc(fin, dof, das);
            DocPrintJob job = ps.createPrintJob();

            job.print(doc, pras);
            fin.close();
            logger.info("打印成功！文件：" + fileName + "数量为：" + count);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (PrintException pe) {
            pe.printStackTrace();
        } finally {
            //IOUtils.closeQuietly(fin);
            IOUtils.closeQuietly(fin);
        }
    }

    /***
     * 用户图形界面的设计
     *
     */
    static class ButtonFrame extends JFrame {
        public JPanel frame;
        public JTextField jtQrcode = new JTextField();
        public JButton jButtonGe = new JButton("二维码生成");
        public JButton jButtonDown = new JButton("二维码下载");
        public JLabel label = new JLabel();
        public int width = 400;
        public int height = 400;
        public String fromat = "png";
        public String pathname;

        public ButtonFrame() {
            frame = new JPanel();
            getContentPane().add(frame, BorderLayout.CENTER);
            frame.setLayout(null);
            frame.setBounds(100, 100, 800, 800);
            this.setSize(800, 800);
            this.setResizable(false);
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            this.setTitle("二维码生成器");
            this.setVisible(true);

            frame.add(jtQrcode);
            jtQrcode.setLocation(10, 10);
            jtQrcode.setBounds(50, 50, 300, 35);

            frame.add(jButtonGe);
            jButtonGe.setLocation(10, 10);
            jButtonGe.setBounds(400, 50, 100, 35);
            jButtonGe.addMouseListener(new MouseAdapter()     //给按钮加上监听事件
            {
                public void mouseClicked(MouseEvent e1) {
                    try {
                        System.out.println(jtQrcode.getText());
                        pathname = this.getClass().getClassLoader().getResource("/") + "new.png";
                        //pathname = Class.class.getClass().getResource("/").getPath() + "new.png";
                        System.out.println(pathname);
                        pathname = generateQRCode(jtQrcode.getText(), width, height, fromat, pathname);
                        frame.add(label);
                        ImageIcon img = new ImageIcon(pathname);
                        img.getImage().flush();
                        label.setBounds(100, 150, 400, 400);
                        label.setIcon(img);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            });
            frame.add(jButtonDown);
            jButtonDown.setLocation(10, 10);
            jButtonDown.setBounds(300, 600, 100, 35);
            jButtonDown.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    printFileAction(pathname, 1);
                }
            });

        }

    }


    public static void main(String[] args) {
        ButtonFrame frame = new ButtonFrame();
        //try {
        //String pathName = generateQRCode(text, width, height, format, "D:/new.png");
        //printFileAction(pathName, 1);
        //System.out.println("生成二维码的图片的路径：\n" + pathName);
        //design(pathName);
        //二维码的解析-解析出二维码的内容
        //String content = parseQRCode(pathName);
        //System.out.println("解析出二维码的图片内容为：\n" + content);
        //} catch (Exception e) {
        //  e.printStackTrace();
        //}

    }

}
