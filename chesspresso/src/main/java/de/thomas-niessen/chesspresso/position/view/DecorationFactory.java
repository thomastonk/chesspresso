/*******************************************************************************
 * Copyright (C) 2019-2024 Thomas Niessen. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 ******************************************************************************/
package chesspresso.position.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chesspresso.Chess;

public class DecorationFactory {

	private DecorationFactory() {
	}

	static public Decoration getArrowDecoration(int from, int to, Color color, Object owner) {
		return new Arrow(from, to, color, owner);
	}

	static public Decoration getBorderDecoration(int square, Color color, Object owner) {
		return new Border(square, color, owner);
	}

	static public Decoration getCircleDecoration(int square, Color color, Object owner) {
		return new Circle(square, color, owner);
	}

	static public Decoration getCrossMarkDecoration(int square, Color color, Object owner) {
		return new CrossMark(square, color, owner);
	}

	static public Decoration getStrokeDecoration(int from, int to, Color color, Object owner) {
		return new Stroke(from, to, color, owner);
	}

	static public Decoration getFramedAreaDecoration(Collection<Integer> squares, Color color, Object owner) {
		return new FramedArea(squares, color, owner);
	}

	static public Decoration getGrayHazeDecoration(Collection<Integer> squares, Object owner) {
		return new GrayHaze(squares, owner);
	}

	static public Decoration getTriangleInCornerDecoration(int square, Color color, Object owner) {
		return new TriangleInCorner(square, color, owner);
	}

	static public Decoration getOneInCornerDecoration(int square, Color color, Object owner) {
		return new OneInCorner(square, color, owner);
	}

	static public Decoration getZeroInCornerDecoration(int square, Color color, Object owner) {
		return new ZeroInCorner(square, color, owner);
	}

	static public Decoration getBarInCornerDecoration(int square, Color color, Object owner) {
		return new BarInCorner(square, color, owner);
	}

	static public Decoration getTextDecoration(int square, Color color, Object owner, String text) {
		return new Text(square, color, owner, text);
	}

	static public Decoration getNumberInSquare(int square, Color color, Object owner, int number) {
		return new NumberInSquare(square, color, owner, number);
	}

	abstract static class AbstractDecoration implements Decoration {

		private final DecorationType type;
		protected final Color color;
		private final Object owner;

		AbstractDecoration(DecorationType type, Color color, Object owner) {
			this.type = type;
			this.color = color;
			this.owner = owner;
		}

		@Override
		public DecorationType getType() {
			return type;
		}

		@Override
		public Color getColor() {
			return color;
		}

		@Override
		public Object getOwner() {
			return owner;
		}

	}

	static class Arrow extends AbstractDecoration {
		private final int from;
		private final int to;

		Arrow(int from, int to, Color color, Object owner) {
			super(DecorationType.ARROW, color, owner);
			this.from = from;
			this.to = to;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);
			g.setStroke(new BasicStroke(squareSize / 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			int from_col, from_row, to_col, to_row;
			if (bottomPlayer == Chess.WHITE) {
				from_col = Chess.sqiToCol(from);
				from_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(from);
				to_col = Chess.sqiToCol(to);
				to_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(to);
			} else {
				from_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(from);
				from_row = Chess.sqiToRow(from);
				to_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(to);
				to_row = Chess.sqiToRow(to);
			}

			int x1 = squareSize * from_col + squareSize / 2;
			int y1 = squareSize * from_row + squareSize / 2;
			int x2 = squareSize * to_col + squareSize / 2;
			int y2 = squareSize * to_row + squareSize / 2;
			Line2D.Double line = new Line2D.Double(x1, y1, x2, y2);

			Polygon arrowHead = new Polygon();
			arrowHead.addPoint(0, 0);
			arrowHead.addPoint(-squareSize / 4, -squareSize / 2 + 1);
			arrowHead.addPoint(squareSize / 4, -squareSize / 2 + 1);
			// Old: bad for highlight castling
			// arrowHead.addPoint(-squareSize / 4, -squareSize / 2);
			// arrowHead.addPoint(squareSize / 4, -squareSize / 2);

			double length = Math.sqrt((from_col - to_col) * (from_col - to_col) + (from_row - to_row) * (from_row - to_row));
			Line2D.Double arrowBody = new Line2D.Double(0, -squareSize / 2, 0, -length * squareSize);

			AffineTransform tx = new AffineTransform(g.getTransform());
			double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
			tx.translate(line.x2, line.y2);
			tx.rotate((angle - Math.PI / 2d));
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setTransform(tx);
			g2.fill(arrowHead);
			g2.draw(arrowBody);
			g2.dispose();
		}
	}

	static class Border extends AbstractDecoration {
		private final int square;

		Border(int square, Color color, Object owner) {
			super(DecorationType.BORDER, color, owner);
			this.square = square;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);
			g.setStroke(new BasicStroke(squareSize / 8));

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}

			g.draw(new RoundRectangle2D.Double(square_col * squareSize + squareSize / 16,
					square_row * squareSize + squareSize / 16, squareSize - squareSize / 8, squareSize - squareSize / 8,
					squareSize / 8, squareSize / 8));
		}
	}

	static class Circle extends AbstractDecoration {
		private final int square;

		Circle(int square, Color color, Object owner) {
			super(DecorationType.CIRCLE, color, owner);
			this.square = square;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}

			Ellipse2D.Double circle = new Ellipse2D.Double(squareSize * square_col + squareSize / 2 - squareSize / 6,
					squareSize * square_row + squareSize / 2 - squareSize / 6, squareSize / 3, squareSize / 3);
			g.fill(circle);
		}
	}

	static class CrossMark extends AbstractDecoration {
		private final int square;

		CrossMark(int square, Color color, Object owner) {
			super(DecorationType.CROSS_MARK, color, owner);
			this.square = square;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);
			g.setStroke(new BasicStroke(squareSize / 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}
			g.drawLine(squareSize * square_col + squareSize / 6, squareSize * square_row + squareSize / 6,
					squareSize * (square_col + 1) - squareSize / 6, squareSize * (square_row + 1) - squareSize / 6);
			g.drawLine(squareSize * square_col + squareSize / 6, squareSize * (square_row + 1) - squareSize / 6,
					squareSize * (square_col + 1) - squareSize / 6, squareSize * square_row + squareSize / 6);
		}
	}

	static class Stroke extends AbstractDecoration {
		private final int from;
		private final int to;

		Stroke(int from, int to, Color color, Object owner) {
			super(DecorationType.STROKE, color, owner);
			this.from = from;
			this.to = to;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);
			g.setStroke(new BasicStroke(squareSize / 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			int from_col, from_row, to_col, to_row;
			if (bottomPlayer == Chess.WHITE) {
				from_col = Chess.sqiToCol(from);
				from_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(from);
				to_col = Chess.sqiToCol(to);
				to_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(to);
			} else {
				from_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(from);
				from_row = Chess.sqiToRow(from);
				to_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(to);
				to_row = Chess.sqiToRow(to);
			}
			g.drawLine(squareSize * from_col + squareSize / 2, squareSize * from_row + squareSize / 2,
					squareSize * to_col + squareSize / 2, squareSize * to_row + squareSize / 2);
		}
	}

	static class FramedArea extends AbstractDecoration {
		private final Set<Integer> squares;

		FramedArea(Collection<Integer> squares, Color color, Object owner) {
			super(DecorationType.FRAMED_AREA, color, owner);
			this.squares = new HashSet<>();
			for (Integer square : squares) {
				if (square >= Chess.A1 && square <= Chess.H8) {
					this.squares.add(square);
				}
			}
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(color);
			g2.setStroke(new BasicStroke(squareSize / 8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			if (bottomPlayer == Chess.BLACK) {
				g2.rotate(Math.PI, 4 * squareSize, 4 * squareSize);
			}

			Area area = new Area();
			for (Integer square : squares) {
				int square_col = Chess.sqiToCol(square);
				int square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
				area.add(new Area(
						new Rectangle2D.Double(square_col * squareSize, square_row * squareSize, (squareSize), (squareSize))));
			}
			g2.draw(area);

			// thicken the frames at the edges of the board
			for (int square = Chess.A1; square <= Chess.H1; ++square) {
				if (squares.contains(square)) {
					int square_col = Chess.sqiToCol(square);
					int square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
					int count = 1;
					++square;
					while (squares.contains(square)) {
						++count;
						++square;
					}
					g2.drawLine(square_col * squareSize + squareSize / 16, (square_row + 1) * squareSize - squareSize / 16,
							(square_col + count) * squareSize - squareSize / 16, (square_row + 1) * squareSize - squareSize / 16);
				}
			}

			for (int square = Chess.A1; square <= Chess.A8; square = square + 8) {
				if (squares.contains(square)) {
					int square_col = Chess.sqiToCol(square);
					int square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
					int count = 1;
					square += 8;
					while (squares.contains(square)) {
						++count;
						square += 8;
					}
					g2.drawLine(square_col * squareSize + squareSize / 16,
							(square_row - count + 1) * squareSize + squareSize / 16, square_col * squareSize + squareSize / 16,
							(square_row + 1) * squareSize - squareSize / 16);
				}
			}

			for (int square = Chess.A8; square <= Chess.H8; ++square) {
				if (squares.contains(square)) {
					int square_col = Chess.sqiToCol(square);
					int square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
					int count = 1;
					++square;
					while (squares.contains(square)) {
						++count;
						++square;
					}
					g2.drawLine(square_col * squareSize + squareSize / 16, square_row * squareSize + squareSize / 16,
							(square_col + count) * squareSize - squareSize / 16, square_row * squareSize + squareSize / 16);
				}
			}

			for (int square = Chess.H1; square <= Chess.H8; square = square + 8) {
				if (squares.contains(square)) {
					int square_col = Chess.sqiToCol(square);
					int square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
					int count = 1;
					square += 8;
					while (squares.contains(square)) {
						++count;
						square += 8;
					}
					g2.drawLine((square_col + 1) * squareSize - squareSize / 16,
							(square_row - count + 1) * squareSize + squareSize / 16,
							(square_col + 1) * squareSize - squareSize / 16, (square_row + 1) * squareSize - squareSize / 16);
				}
			}

			g2.dispose();
		}
	}

	static class GrayHaze extends AbstractDecoration {
		private final static Color COLOR = new Color(160, 160, 160, 160);

		private final List<Integer> squares;

		GrayHaze(Collection<Integer> squares, Object owner) {
			super(DecorationType.GRAY_HAZE, COLOR, owner);
			this.squares = new ArrayList<>();
			for (Integer square : squares) {
				if (square >= Chess.A1 && square <= Chess.H8) {
					this.squares.add(square);
				}
			}
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);
			for (Integer square : squares) {
				int square_col, square_row;
				if (bottomPlayer == Chess.WHITE) {
					square_col = Chess.sqiToCol(square);
					square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
				} else {
					square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
					square_row = Chess.sqiToRow(square);
				}

				g.fillRect(square_col * squareSize, square_row * squareSize, squareSize, squareSize);
			}
		}
	}

	static class TriangleInCorner extends AbstractDecoration {
		private final int square;

		TriangleInCorner(int square, Color color, Object owner) {
			super(DecorationType.TRIANGLE_IN_CORNER, color, owner);
			this.square = square;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}

			Polygon triangle = new Polygon();
			int x0 = squareSize * square_col + squareSize;
			int y0 = squareSize * square_row;
			triangle.addPoint(x0 - 15, y0 + 3);
			triangle.addPoint(x0 - 3, y0 + 3);
			triangle.addPoint(x0 - 9, y0 + 10);

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.fill(triangle);
			g2.dispose();
		}
	}

	static class OneInCorner extends AbstractDecoration {
		private final int square;

		OneInCorner(int square, Color color, Object owner) {
			super(DecorationType.ONE_IN_CORNER, color, owner);
			this.square = square;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}

			Polygon one = new Polygon();
			int x0 = squareSize * square_col + squareSize;
			int y0 = squareSize * square_row;
			one.addPoint(x0 - 7, y0 + 3);
			one.addPoint(x0 - 4, y0 + 3);
			one.addPoint(x0 - 4, y0 + 11);
			one.addPoint(x0 - 7, y0 + 11);
			one.addPoint(x0 - 7, y0 + 5);
			one.addPoint(x0 - 10, y0 + 5);

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(color);
			g2.fill(one);
			g2.setStroke(new BasicStroke(.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(Color.black);
			g2.draw(one);
			g2.dispose();
		}
	}

	static class ZeroInCorner extends AbstractDecoration {
		private final int square;

		ZeroInCorner(int square, Color color, Object owner) {
			super(DecorationType.ZERO_IN_CORNER, color, owner);
			this.square = square;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}

			Polygon zero = new Polygon();
			int x0 = squareSize * square_col + squareSize;
			int y0 = squareSize * square_row;
			zero.addPoint(x0 - 3, y0 + 3);
			zero.addPoint(x0 - 3, y0 + 11);
			zero.addPoint(x0 - 8, y0 + 11);
			zero.addPoint(x0 - 8, y0 + 3);
			zero.addPoint(x0 - 3, y0 + 3);

			zero.addPoint(x0 - 5, y0 + 5);
			zero.addPoint(x0 - 5, y0 + 9);
			zero.addPoint(x0 - 6, y0 + 9);
			zero.addPoint(x0 - 6, y0 + 5);
			zero.addPoint(x0 - 5, y0 + 5);

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(color);
			g2.fill(zero);
			g2.setStroke(new BasicStroke(.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(Color.black);
			g2.draw(zero);
			g2.dispose();
		}
	}

	static class BarInCorner extends AbstractDecoration {
		private final int square;

		BarInCorner(int square, Color color, Object owner) {
			super(DecorationType.BAR_IN_CORNER, color, owner);
			this.square = square;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}

			Polygon bar = new Polygon();
			int x0 = squareSize * square_col + squareSize;
			int y0 = squareSize * square_row;
			bar.addPoint(x0 - 12, y0 + 4);
			bar.addPoint(x0 - 2, y0 + 4);
			bar.addPoint(x0 - 2, y0 + 7);
			bar.addPoint(x0 - 12, y0 + 7);

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(color);
			g2.fill(bar);
			g2.setStroke(new BasicStroke(.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(Color.black);
			g2.draw(bar);
			g2.dispose();
		}
	}

	static class Text extends AbstractDecoration {
		private final int square;
		private final String text;

		Text(int square, Color color, Object owner, String text) {
			super(DecorationType.TEXT, color, owner);
			this.square = square;
			this.text = text;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}

			int x0 = squareSize * square_col + squareSize;
			int y0 = squareSize * square_row;

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setFont(new Font(Font.DIALOG, Font.PLAIN, squareSize - 12));
			g2.drawString(text, x0, y0);
			g2.dispose();
		}
	}

	static class NumberInSquare extends AbstractDecoration {

		private final int square;
		private final int number;

		NumberInSquare(int square, Color color, Object owner, int number) {
			super(DecorationType.NUMBER_IN_SQUARE, color, owner);
			this.square = square;
			this.number = number;
		}

		@Override
		public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
			g.setColor(color);

			int square_col, square_row;
			if (bottomPlayer == Chess.WHITE) {
				square_col = Chess.sqiToCol(square);
				square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
			} else {
				square_col = Chess.NUM_OF_COLS - 1 - Chess.sqiToCol(square);
				square_row = Chess.sqiToRow(square);
			}

			int x0 = squareSize * (square_col - 1) + squareSize + 12; // some mystic numbers
			int y0 = squareSize * (square_row + 1) - 10;

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setFont(new Font(Font.DIALOG, Font.PLAIN, squareSize - 12));
			g2.drawString(Integer.toString(number), x0, y0);
			g2.dispose();
		}

		@Override
		public int getSquare() {
			return square;
		}
	}
}
