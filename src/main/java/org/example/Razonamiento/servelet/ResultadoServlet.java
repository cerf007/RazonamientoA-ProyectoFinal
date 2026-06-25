package org.example.Razonamiento.servelet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet(name = "ResultadoServlet", urlPatterns = {"/resultado"})
public class ResultadoServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("resultadoPD") == null) {
            resp.sendRedirect(req.getContextPath() + "/portal");
            return;
        }
        req.setAttribute("pd",        session.getAttribute("resultadoPD"));
        req.setAttribute("percentil", session.getAttribute("resultadoPercentil"));
        req.setAttribute("interp",    session.getAttribute("resultadoInterp"));
        req.setAttribute("nombre",    session.getAttribute("evaluadoNombre"));
        session.removeAttribute("resultadoPD");
        session.removeAttribute("resultadoPercentil");
        session.removeAttribute("resultadoInterp");
        req.getRequestDispatcher("/WEB-INF/portal/resultado.jsp").forward(req, resp);
    }
}
