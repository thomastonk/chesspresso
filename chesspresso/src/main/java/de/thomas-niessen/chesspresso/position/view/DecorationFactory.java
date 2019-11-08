package chesspresso.position.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
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

    static public Decoration getArrowDecoration(int from, int to, Color color) {
	return new Arrow(from, to, color);
    }

    static public Decoration getBorderDecoration(int square, Color color) {
	return new Border(square, color);
    }

    static public Decoration getCircleDecoration(int square, Color color) {
	return new Circle(square, color);
    }

    static public Decoration getStrokeDecoration(int from, int to, Color color) {
	return new Stroke(from, to, color);
    }

    static public Decoration getFramedAreaDecoration(Collection<Integer> squares, Color color) {
	return new FramedArea(squares, color);
    }

    static public Decoration getGrayHazeDecoration(Collection<Integer> squares) {
	return new GrayHaze(squares);
    }

    static class Arrow implements Decoration {
	private final int from;
	private final int to;
	private final Color color;

	Arrow(int from, int to, Color color) {
	    this.from = from;
	    this.to = to;
	    this.color = color;
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
	    arrowHead.addPoint(-squareSize / 4, -squareSize / 2);
	    arrowHead.addPoint(squareSize / 4, -squareSize / 2);

	    double length = Math
		    .sqrt((from_col - to_col) * (from_col - to_col) + (from_row - to_row) * (from_row - to_row));
	    Line2D.Double arrowBody = new Line2D.Double(0, -squareSize / 2, 0, -length * squareSize);

	    AffineTransform tx = new AffineTransform(g.getTransform());
	    double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
	    tx.translate(line.x2, line.y2);
	    tx.rotate((angle - Math.PI / 2d));
	    Graphics2D g2 = (Graphics2D) g.create();
	    g2.setTransform(tx);
	    g2.fill(arrowHead);
	    g2.draw(arrowBody);
	    g2.dispose();
	}

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.ARROW;
	}
    }

    static class Border implements Decoration {
	private final int square;
	private final Color color;

	Border(int square, Color color) {
	    this.square = square;
	    this.color = color;
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

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.BORDER;
	}
    }

    static class Circle implements Decoration {
	private final int square;
	private final Color color;

	Circle(int square, Color color) {
	    this.square = square;
	    this.color = color;
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

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.CIRCLE;
	}
    }

    static class Stroke implements Decoration {
	private final int from;
	private final int to;
	private final Color color;

	Stroke(int from, int to, Color color) {
	    this.from = from;
	    this.to = to;
	    this.color = color;
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

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.STROKE;
	}
    }

    static class FramedArea implements Decoration {
	private final Set<Integer> squares;
	private final Color color;

	FramedArea(Collection<Integer> squares, Color color) {
	    this.squares = new HashSet<>();
	    for (Integer square : squares) {
		if (square >= Chess.A1 && square <= Chess.H8) {
		    this.squares.add(square);
		}
	    }
	    this.color = color;
	}

	@Override
	public void paint(Graphics2D g, int squareSize, int bottomPlayer) {
	    Graphics2D g2 = (Graphics2D) g.create();
	    g2.setColor(color);
	    g2.setStroke(new BasicStroke(squareSize / 8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
	    if (bottomPlayer == Chess.BLACK) {
		g2.rotate(Math.PI, 4 * squareSize, 4 * squareSize);
	    }

	    Area area = new Area();
	    for (Integer square : squares) {
		int square_col = Chess.sqiToCol(square);
		int square_row = Chess.NUM_OF_ROWS - 1 - Chess.sqiToRow(square);
		area.add(new Area(new Rectangle2D.Double((double) (square_col * squareSize),
			(double) (square_row * squareSize), (double) (squareSize), (double) (squareSize))));
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
		    g2.drawLine(square_col * squareSize + squareSize / 16,
			    (square_row + 1) * squareSize - squareSize / 16,
			    (square_col + count) * squareSize - squareSize / 16,
			    (square_row + 1) * squareSize - squareSize / 16);
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
			    (square_row - count + 1) * squareSize + squareSize / 16,
			    square_col * squareSize + squareSize / 16, (square_row + 1) * squareSize - squareSize / 16);
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
			    (square_col + count) * squareSize - squareSize / 16,
			    square_row * squareSize + squareSize / 16);
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
			    (square_col + 1) * squareSize - squareSize / 16,
			    (square_row + 1) * squareSize - squareSize / 16);
		}
	    }

	    g2.dispose();
	}

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.FRAMED_AREA;
	}
    }

    static class GrayHaze implements Decoration {
	private final List<Integer> squares;

	private final static Color color = new Color(160, 160, 160, 160);

	GrayHaze(Collection<Integer> squares) {
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

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.GRAY_HAZE;
	}
    }
}
