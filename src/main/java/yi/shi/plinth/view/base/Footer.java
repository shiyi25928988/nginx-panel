package yi.shi.plinth.view.base;

import j2html.tags.specialized.FooterTag;

import static j2html.TagCreator.div;
import static j2html.TagCreator.footer;

/**
 * 页脚。
 */
public class Footer {

    public static FooterTag createFooter(String color) {
        // 默认与登录页背景同色（blue-grey darken-3 = #37474f），header/footer/登录页一体
        String c = (color == null || color.isEmpty()) ? "blue-grey darken-3" : color;
        return footer().withClass("page-footer " + c).with(
                div().withClass("footer-copyright container").withText("Nginx Shell © 2026")
        );
    }
}
