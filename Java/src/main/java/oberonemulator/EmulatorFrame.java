package oberonemulator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class EmulatorFrame extends JFrame {

	private CPU cpu;
	private final Memory mem;
	private MemoryMappedIO mmio;
	private Keyboard keyboard;
	private boolean largeAddressSpace;

	public EmulatorFrame(final Memory mem, Keyboard keyboard, MemoryMappedIO mmio, BufferedImage img, ImageMemory imgmem, boolean largeAddressSpace) {
		super("Oberon Emulator");
		this.mmio = mmio;
		this.keyboard = keyboard;
		setLayout(new BorderLayout());
		this.mem = mem;
		this.largeAddressSpace = largeAddressSpace;
		cpu = new CPU(mem, largeAddressSpace);
		EmulatorPanel ep = new EmulatorPanel(img, mmio);
		imgmem.setObserver(ep);
		ep.setFocusable(true);
		ep.setFocusTraversalKeysEnabled(false);
		add(BorderLayout.CENTER, new JScrollPane(ep));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		ep.requestFocus();
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				cpu.dispose();
				dispose();
			}
		});
		cpu.start();
	}

	public class EmulatorPanel extends JPanel {

		private BufferedImage img;

		public EmulatorPanel(final BufferedImage img, final MemoryMappedIO mmio) {
			this.img = img;
			BufferedImage cursorImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			setCursor(Toolkit.getDefaultToolkit().createCustomCursor(cursorImage, new Point(0, 0), "empty"));

			addKeyListener(new KeyAdapter() {

				@Override
				public void keyTyped(KeyEvent e) {
					keyboard.type(e.getKeyChar());
				}

				@Override
				public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_F12) {
						cpu.reset(e.isShiftDown());
						if (!cpu.isAlive()) {
							CPU oldCPU = cpu;
							cpu = new CPU(mem, largeAddressSpace);
							if (!e.isShiftDown()) {
								cpu.copyRegisters(oldCPU);
							}
							cpu.start();
						}
					} else if (e.getKeyCode() == KeyEvent.VK_F1 || e.getKeyCode() == KeyEvent.VK_INSERT) {
						keyboard.press(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F1);
					} else if (e.getKeyCode() == KeyEvent.VK_ALT && !e.isControlDown()) {
						mmio.mouseButton(2, true);
					} else {
						keyboard.press(e.getKeyLocation(), e.getKeyCode());
					}
				}

				@Override
				public void keyReleased(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_ALT) {
						mmio.mouseButton(2, false);
					} else if (e.getKeyCode() == KeyEvent.VK_F1 || e.getKeyCode() == KeyEvent.VK_INSERT) {
						keyboard.release(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F1);
					} else if (e.getKeyCode() != KeyEvent.VK_F12) {
						keyboard.release(e.getKeyLocation(), e.getKeyCode());
					}
				}
			});
			addMouseListener(new MouseAdapter() {

				@Override
				public void mouseReleased(MouseEvent e) {
					mmio.mouseButton(e.getButton(), false);
				}

				@Override
				public void mousePressed(MouseEvent e) {
					mmio.mouseButton(e.getButton(), true);
				}
			});

			addMouseMotionListener(new MouseMotionListener() {

				@Override
				public void mouseMoved(MouseEvent e) {
					int scaled_x = e.getX();
					int scaled_y = e.getY();
					int x = Math.min(scaled_x, img.getWidth() - 1);
					int y = Math.min(scaled_y, img.getHeight() - 1);
					mmio.mouseMoved(x, img.getHeight() - y - 1);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					mouseMoved(e);
				}
			});
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(img.getWidth(), img.getHeight());
		}

		@Override
		protected void paintComponent(Graphics g) {
			EmulatorFrame.this.setTitle(mmio.getLEDs() + " - Oberon Emulator");
			g.drawImage(img, 0, 0, Color.RED, this);
			if (!cpu.isAlive()) {
				g.setColor(new Color(255, 0, 0, 100));
				g.fillRect(0, 0, img.getWidth(), img.getHeight());
			}
		}
	}
}
