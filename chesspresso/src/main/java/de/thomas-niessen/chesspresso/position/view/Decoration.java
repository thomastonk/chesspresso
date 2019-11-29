package chesspresso.position.view;

import java.awt.Color;
import java.awt.Graphics2D;

public interface Decoration {

    public enum DecorationType {
	ARROW, BORDER, CIRCLE, CROSS_MARK, STROKE, FRAMED_AREA, GRAY_HAZE
    };

    void paint(Graphics2D g, int squareSize, int bottomPlayer);

    Color getColor();

    DecorationType getType();

}
