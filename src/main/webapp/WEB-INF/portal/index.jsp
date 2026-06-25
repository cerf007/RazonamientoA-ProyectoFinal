<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BFA Razonamiento — Acceso al Sistema</title>
    <meta name="description" content="Portal de acceso al sistema de evaluación psicométrica BFA Forma A. Ingrese como evaluador o evaluado.">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        /* ═══════════════════════════════════════════════════════
           RESET & FOUNDATIONS
           ═══════════════════════════════════════════════════════ */
        *, *::before, *::after {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        :root {
            --primary-gradient: linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%);
            --accent-blue: #6c63ff;
            --accent-cyan: #00d2ff;
            --accent-purple: #7c3aed;
            --accent-pink: #ec4899;
            --glass-bg: rgba(255, 255, 255, 0.06);
            --glass-border: rgba(255, 255, 255, 0.12);
            --glass-hover: rgba(255, 255, 255, 0.10);
            --text-primary: #f1f5f9;
            --text-secondary: #94a3b8;
            --text-muted: #64748b;
            --error-red: #ef4444;
            --error-bg: rgba(239, 68, 68, 0.12);
            --success-green: #22c55e;
            --input-bg: rgba(255, 255, 255, 0.05);
            --input-border: rgba(255, 255, 255, 0.10);
            --input-focus: rgba(108, 99, 255, 0.5);
            --radius-sm: 8px;
            --radius-md: 12px;
            --radius-lg: 16px;
            --radius-xl: 24px;
            --shadow-glow: 0 0 40px rgba(108, 99, 255, 0.15);
            --transition-fast: 0.2s cubic-bezier(0.4, 0, 0.2, 1);
            --transition-smooth: 0.4s cubic-bezier(0.4, 0, 0.2, 1);
        }

        html, body {
            height: 100%;
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
            background: var(--primary-gradient);
            color: var(--text-primary);
            overflow-x: hidden;
            -webkit-font-smoothing: antialiased;
        }

        /* ═══════════════════════════════════════════════════════
           ANIMATED BACKGROUND
           ═══════════════════════════════════════════════════════ */
        .bg-effects {
            position: fixed;
            inset: 0;
            z-index: 0;
            overflow: hidden;
            pointer-events: none;
        }

        .bg-orb {
            position: absolute;
            border-radius: 50%;
            filter: blur(100px);
            opacity: 0.25;
            animation: orbFloat 18s ease-in-out infinite;
        }

        .bg-orb--1 {
            width: 500px;
            height: 500px;
            background: var(--accent-blue);
            top: -10%;
            left: -5%;
            animation-delay: 0s;
        }

        .bg-orb--2 {
            width: 400px;
            height: 400px;
            background: var(--accent-purple);
            bottom: -10%;
            right: -5%;
            animation-delay: -6s;
        }

        .bg-orb--3 {
            width: 300px;
            height: 300px;
            background: var(--accent-cyan);
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            animation-delay: -12s;
            opacity: 0.12;
        }

        @keyframes orbFloat {
            0%, 100% { transform: translate(0, 0) scale(1); }
            33% { transform: translate(30px, -40px) scale(1.05); }
            66% { transform: translate(-20px, 20px) scale(0.95); }
        }

        /* ═══════════════════════════════════════════════════════
           MAIN LAYOUT
           ═══════════════════════════════════════════════════════ */
        .login-container {
            position: relative;
            z-index: 1;
            display: flex;
            min-height: 100vh;
            width: 100%;
        }

        .login-panel {
            flex: 1;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 40px 32px;
            position: relative;
        }

        /* ── Divider ── */
        .login-panel--left::after {
            content: '';
            position: absolute;
            right: 0;
            top: 15%;
            height: 70%;
            width: 1px;
            background: linear-gradient(180deg,
                transparent 0%,
                var(--glass-border) 30%,
                rgba(108, 99, 255, 0.3) 50%,
                var(--glass-border) 70%,
                transparent 100%);
        }

        /* ═══════════════════════════════════════════════════════
           LEFT PANEL — EVALUADOR
           ═══════════════════════════════════════════════════════ */
        .evaluador-content {
            text-align: center;
            max-width: 420px;
            animation: fadeInUp 0.8s ease-out;
        }

        .evaluador-icon {
            width: 120px;
            height: 120px;
            margin: 0 auto 32px;
            border-radius: 50%;
            background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple));
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: var(--shadow-glow),
                        0 0 60px rgba(108, 99, 255, 0.2);
            position: relative;
            transition: var(--transition-smooth);
        }

        .evaluador-icon:hover {
            transform: scale(1.06);
            box-shadow: 0 0 60px rgba(108, 99, 255, 0.35),
                        0 0 100px rgba(108, 99, 255, 0.15);
        }

        .evaluador-icon::before {
            content: '';
            position: absolute;
            inset: -3px;
            border-radius: 50%;
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple), var(--accent-pink));
            z-index: -1;
            opacity: 0.5;
            animation: iconPulse 3s ease-in-out infinite;
        }

        @keyframes iconPulse {
            0%, 100% { opacity: 0.3; transform: scale(1); }
            50% { opacity: 0.6; transform: scale(1.04); }
        }

        .evaluador-icon svg {
            width: 52px;
            height: 52px;
            fill: none;
            stroke: white;
            stroke-width: 1.8;
            stroke-linecap: round;
            stroke-linejoin: round;
        }

        .panel-tag {
            display: inline-block;
            font-size: 0.7rem;
            font-weight: 600;
            letter-spacing: 2.5px;
            text-transform: uppercase;
            color: var(--accent-cyan);
            margin-bottom: 12px;
            padding: 6px 16px;
            background: rgba(0, 210, 255, 0.08);
            border: 1px solid rgba(0, 210, 255, 0.15);
            border-radius: 100px;
        }

        .panel-title {
            font-size: 2rem;
            font-weight: 800;
            letter-spacing: -0.5px;
            margin-bottom: 14px;
            background: linear-gradient(135deg, #fff 30%, var(--accent-cyan));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }

        .panel-description {
            font-size: 0.95rem;
            line-height: 1.65;
            color: var(--text-secondary);
            margin-bottom: 36px;
        }

        .btn-evaluador {
            display: inline-flex;
            align-items: center;
            gap: 10px;
            padding: 16px 40px;
            font-family: 'Inter', sans-serif;
            font-size: 1rem;
            font-weight: 600;
            color: white;
            background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple));
            border: none;
            border-radius: var(--radius-md);
            cursor: pointer;
            text-decoration: none;
            transition: var(--transition-smooth);
            position: relative;
            overflow: hidden;
            box-shadow: 0 4px 24px rgba(108, 99, 255, 0.3);
        }

        .btn-evaluador::before {
            content: '';
            position: absolute;
            inset: 0;
            background: linear-gradient(135deg, var(--accent-purple), var(--accent-pink));
            opacity: 0;
            transition: opacity var(--transition-smooth);
        }

        .btn-evaluador:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 36px rgba(108, 99, 255, 0.45);
        }

        .btn-evaluador:hover::before {
            opacity: 1;
        }

        .btn-evaluador span,
        .btn-evaluador svg {
            position: relative;
            z-index: 1;
        }

        .btn-evaluador svg {
            width: 20px;
            height: 20px;
            stroke: white;
            fill: none;
            stroke-width: 2;
            stroke-linecap: round;
            stroke-linejoin: round;
            transition: transform var(--transition-fast);
        }

        .btn-evaluador:hover svg {
            transform: translateX(4px);
        }

        /* ═══════════════════════════════════════════════════════
           RIGHT PANEL — EVALUADO
           ═══════════════════════════════════════════════════════ */
        .evaluado-content {
            width: 100%;
            max-width: 480px;
            animation: fadeInUp 0.8s ease-out 0.15s both;
        }

        .evaluado-header {
            text-align: center;
            margin-bottom: 32px;
        }

        .evaluado-icon {
            width: 80px;
            height: 80px;
            margin: 0 auto 20px;
            border-radius: var(--radius-lg);
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-blue));
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 32px rgba(0, 210, 255, 0.2);
        }

        .evaluado-icon svg {
            width: 36px;
            height: 36px;
            fill: none;
            stroke: white;
            stroke-width: 1.8;
            stroke-linecap: round;
            stroke-linejoin: round;
        }

        .evaluado-header .panel-tag {
            color: var(--accent-pink);
            background: rgba(236, 72, 153, 0.08);
            border-color: rgba(236, 72, 153, 0.15);
        }

        .evaluado-header .panel-title {
            font-size: 1.6rem;
            background: linear-gradient(135deg, #fff 30%, var(--accent-pink));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }

        /* ── Glass Form Card ── */
        .form-card {
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            border-radius: var(--radius-xl);
            padding: 32px 28px;
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
        }

        /* ── Error Alert ── */
        .error-alert {
            display: flex;
            align-items: flex-start;
            gap: 12px;
            padding: 14px 18px;
            background: var(--error-bg);
            border: 1px solid rgba(239, 68, 68, 0.25);
            border-radius: var(--radius-md);
            margin-bottom: 24px;
            animation: shakeAlert 0.5s ease-out;
        }

        .error-alert svg {
            width: 20px;
            height: 20px;
            stroke: var(--error-red);
            fill: none;
            stroke-width: 2;
            flex-shrink: 0;
            margin-top: 1px;
        }

        .error-alert p {
            font-size: 0.85rem;
            color: #fca5a5;
            line-height: 1.5;
        }

        @keyframes shakeAlert {
            0%, 100% { transform: translateX(0); }
            15% { transform: translateX(-6px); }
            30% { transform: translateX(5px); }
            45% { transform: translateX(-4px); }
            60% { transform: translateX(3px); }
            75% { transform: translateX(-2px); }
        }

        /* ── Form Grid ── */
        .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 16px;
            margin-bottom: 16px;
        }

        .form-row--full {
            grid-template-columns: 1fr;
        }

        .form-group {
            display: flex;
            flex-direction: column;
            gap: 6px;
        }

        .form-label {
            font-size: 0.78rem;
            font-weight: 500;
            color: var(--text-secondary);
            letter-spacing: 0.3px;
        }

        .form-label .required {
            color: var(--accent-pink);
            margin-left: 2px;
        }

        .form-input,
        .form-select {
            width: 100%;
            padding: 12px 16px;
            font-family: 'Inter', sans-serif;
            font-size: 0.9rem;
            color: var(--text-primary);
            background: var(--input-bg);
            border: 1px solid var(--input-border);
            border-radius: var(--radius-sm);
            outline: none;
            transition: var(--transition-fast);
            -webkit-appearance: none;
        }

        .form-input::placeholder {
            color: var(--text-muted);
        }

        .form-input:focus,
        .form-select:focus {
            border-color: var(--accent-blue);
            box-shadow: 0 0 0 3px var(--input-focus);
            background: rgba(255, 255, 255, 0.07);
        }

        .form-select {
            cursor: pointer;
            background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' fill='%2394a3b8' viewBox='0 0 16 16'%3E%3Cpath d='M1.6 5.2L8 11.6 14.4 5.2'/%3E%3C/svg%3E");
            background-repeat: no-repeat;
            background-position: right 14px center;
            padding-right: 40px;
        }

        .form-select option {
            background: #1e1b4b;
            color: var(--text-primary);
        }

        /* ── Divider ── */
        .form-divider {
            display: flex;
            align-items: center;
            gap: 16px;
            margin: 24px 0;
        }

        .form-divider::before,
        .form-divider::after {
            content: '';
            flex: 1;
            height: 1px;
            background: var(--glass-border);
        }

        .form-divider span {
            font-size: 0.72rem;
            font-weight: 600;
            letter-spacing: 2px;
            text-transform: uppercase;
            color: var(--text-muted);
            white-space: nowrap;
        }

        /* ── Code Input Special ── */
        .code-input-wrapper {
            position: relative;
        }

        .code-input-wrapper .form-input {
            padding-left: 48px;
            font-size: 1.05rem;
            font-weight: 600;
            letter-spacing: 2px;
            text-transform: uppercase;
            text-align: center;
            border-color: rgba(108, 99, 255, 0.25);
            background: rgba(108, 99, 255, 0.06);
        }

        .code-input-wrapper .form-input:focus {
            border-color: var(--accent-blue);
            background: rgba(108, 99, 255, 0.1);
        }

        .code-input-wrapper svg {
            position: absolute;
            left: 16px;
            top: 50%;
            transform: translateY(-50%);
            width: 20px;
            height: 20px;
            stroke: var(--accent-blue);
            fill: none;
            stroke-width: 2;
            stroke-linecap: round;
            stroke-linejoin: round;
        }

        /* ── Submit Button ── */
        .btn-submit {
            width: 100%;
            padding: 14px;
            margin-top: 8px;
            font-family: 'Inter', sans-serif;
            font-size: 0.95rem;
            font-weight: 600;
            color: white;
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-blue));
            border: none;
            border-radius: var(--radius-md);
            cursor: pointer;
            transition: var(--transition-smooth);
            position: relative;
            overflow: hidden;
            box-shadow: 0 4px 20px rgba(0, 210, 255, 0.25);
        }

        .btn-submit::before {
            content: '';
            position: absolute;
            inset: 0;
            background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple));
            opacity: 0;
            transition: opacity var(--transition-smooth);
        }

        .btn-submit:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 32px rgba(0, 210, 255, 0.4);
        }

        .btn-submit:hover::before {
            opacity: 1;
        }

        .btn-submit span {
            position: relative;
            z-index: 1;
        }

        .btn-submit:active {
            transform: translateY(0);
        }

        /* ═══════════════════════════════════════════════════════
           FOOTER
           ═══════════════════════════════════════════════════════ */
        .login-footer {
            position: absolute;
            bottom: 24px;
            left: 50%;
            transform: translateX(-50%);
            text-align: center;
        }

        .login-footer p {
            font-size: 0.72rem;
            color: var(--text-muted);
            letter-spacing: 0.5px;
        }

        /* ═══════════════════════════════════════════════════════
           ANIMATIONS
           ═══════════════════════════════════════════════════════ */
        @keyframes fadeInUp {
            from {
                opacity: 0;
                transform: translateY(28px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }

        /* ═══════════════════════════════════════════════════════
           RESPONSIVE
           ═══════════════════════════════════════════════════════ */
        @media (max-width: 900px) {
            .login-container {
                flex-direction: column;
            }

            .login-panel--left::after {
                display: none;
            }

            .login-panel--left {
                padding-bottom: 20px;
            }

            .login-panel--right {
                padding-top: 0;
            }

            .evaluador-icon {
                width: 90px;
                height: 90px;
            }

            .evaluador-icon svg {
                width: 40px;
                height: 40px;
            }

            .panel-title {
                font-size: 1.6rem;
            }

            .form-row {
                grid-template-columns: 1fr;
            }
        }

        @media (max-width: 480px) {
            .login-panel {
                padding: 24px 16px;
            }

            .form-card {
                padding: 24px 18px;
            }
        }
    </style>
</head>
<body>

<!-- ═══ Animated Background ═══ -->
<div class="bg-effects">
    <div class="bg-orb bg-orb--1"></div>
    <div class="bg-orb bg-orb--2"></div>
    <div class="bg-orb bg-orb--3"></div>
</div>

<!-- ═══ Main Layout ═══ -->
<div class="login-container">

    <!-- ────────────────────────────────────────────────
         LEFT PANEL — EVALUADOR
         ──────────────────────────────────────────────── -->
    <div class="login-panel login-panel--left">
        <div class="evaluador-content">
            <div class="evaluador-icon" id="evaluador-icon">
                <svg viewBox="0 0 24 24">
                    <path d="M12 15c-3.87 0-7 1.57-7 3.5V21h14v-2.5c0-1.93-3.13-3.5-7-3.5z"/>
                    <circle cx="12" cy="8" r="4"/>
                    <path d="M20 8h-2M21 12h-3M20 16h-2"/>
                </svg>
            </div>
            <span class="panel-tag">Panel Administrativo</span>
            <h1 class="panel-title">Evaluador</h1>
            <p class="panel-description">
                Accede al sistema de gestión OpenXava para administrar preguntas, 
                generar códigos de sesión, revisar resultados y auditar evaluaciones.
            </p>
            <a href="<%= request.getContextPath() %>/m/SignIn" class="btn-evaluador" id="btn-evaluador">
                <span>Entrar como Evaluador</span>
                <svg viewBox="0 0 24 24">
                    <line x1="5" y1="12" x2="19" y2="12"/>
                    <polyline points="12 5 19 12 12 19"/>
                </svg>
            </a>
        </div>

        <div class="login-footer">
            <p>&copy; 2026 — BFA Razonamiento Forma A</p>
        </div>
    </div>

    <!-- ────────────────────────────────────────────────
         RIGHT PANEL — EVALUADO
         ──────────────────────────────────────────────── -->
    <div class="login-panel login-panel--right">
        <div class="evaluado-content">
            <div class="evaluado-header">
                <div class="evaluado-icon" id="evaluado-icon">
                    <svg viewBox="0 0 24 24">
                        <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                        <line x1="12" y1="6" x2="12" y2="12"/>
                        <line x1="9" y1="9" x2="15" y2="9"/>
                    </svg>
                </div>
                <span class="panel-tag">Portal de Evaluación</span>
                <h2 class="panel-title">Evaluado</h2>
            </div>

            <div class="form-card">

                <!-- Error Message -->
                <% String error = (String) request.getAttribute("error"); %>
                <% String valNombre = (String) request.getAttribute("nombre"); %>
                <% String valApellido = (String) request.getAttribute("apellido"); %>
                <% String valCorreo = (String) request.getAttribute("correo"); %>
                <% String valTelefono = (String) request.getAttribute("telefono"); %>
                <% String valSexo = (String) request.getAttribute("sexo"); %>
                <% String valFechaNac = (String) request.getAttribute("fechaNacimiento"); %>
                <% String valCodigo = (String) request.getAttribute("codigoSesion"); %>
                <% if (error != null && !error.isEmpty()) { %>
                <div class="error-alert" id="error-alert">
                    <svg viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"/>
                        <line x1="12" y1="8" x2="12" y2="12"/>
                        <line x1="12" y1="16" x2="12.01" y2="16"/>
                    </svg>
                    <p><%= error %></p>
                </div>
                <% } %>

                <form action="<%= request.getContextPath() %>/portal" method="POST" id="form-evaluado" autocomplete="off">

                    <!-- Row: Nombre + Apellido -->
                    <div class="form-row">
                        <div class="form-group">
                            <label class="form-label" for="nombre">
                                Nombre <span class="required">*</span>
                            </label>
                            <input type="text" class="form-input" id="nombre" name="nombre"
                                   placeholder="Tu nombre" required maxlength="100"
                                   value="<%= valNombre != null ? valNombre : "" %>">
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="apellido">
                                Apellido <span class="required">*</span>
                            </label>
                            <input type="text" class="form-input" id="apellido" name="apellido"
                                   placeholder="Tu apellido" required maxlength="100"
                                   value="<%= valApellido != null ? valApellido : "" %>">
                        </div>
                    </div>

                    <!-- Row: Correo -->
                    <div class="form-row form-row--full">
                        <div class="form-group">
                            <label class="form-label" for="correo">
                                Correo Electrónico <span class="required">*</span>
                            </label>
                            <input type="email" class="form-input" id="correo" name="correo"
                                   placeholder="ejemplo@correo.com" required maxlength="150"
                                   value="<%= valCorreo != null ? valCorreo : "" %>">
                        </div>
                    </div>

                    <!-- Row: Teléfono + Sexo -->
                    <div class="form-row">
                        <div class="form-group">
                            <label class="form-label" for="telefono">
                                Teléfono
                            </label>
                            <input type="tel" class="form-input" id="telefono" name="telefono"
                                   placeholder="+505 8888-0000" maxlength="20"
                                   value="<%= valTelefono != null ? valTelefono : "" %>">
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="sexo">
                                Sexo
                            </label>
                            <select class="form-select" id="sexo" name="sexo">
                                <option value="" <%= (valSexo == null || valSexo.isEmpty()) ? "selected" : "" %>>Seleccionar</option>
                                <option value="M" <%= "M".equals(valSexo) ? "selected" : "" %>>Masculino</option>
                                <option value="F" <%= "F".equals(valSexo) ? "selected" : "" %>>Femenino</option>
                            </select>
                        </div>
                    </div>

                    <!-- Row: Fecha Nacimiento -->
                    <div class="form-row form-row--full">
                        <div class="form-group">
                            <label class="form-label" for="fechaNacimiento">
                                Fecha de Nacimiento
                            </label>
                            <input type="date" class="form-input" id="fechaNacimiento" name="fechaNacimiento"
                                   value="<%= valFechaNac != null ? valFechaNac : "" %>">
                        </div>
                    </div>

                    <!-- ── Divider ── -->
                    <div class="form-divider">
                        <span>Código de Acceso</span>
                    </div>

                    <!-- Row: Código de Sesión -->
                    <div class="form-row form-row--full">
                        <div class="form-group">
                            <div class="code-input-wrapper">
                                <svg viewBox="0 0 24 24">
                                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                                </svg>
                                <input type="text" class="form-input" id="codigoSesion" name="codigoSesion"
                                       placeholder="XXXXXXXX" required maxlength="10"
                                       autocomplete="off" spellcheck="false"
                                       value="<%= valCodigo != null ? valCodigo : "" %>">
                            </div>
                        </div>
                    </div>

                    <!-- Submit -->
                    <button type="submit" class="btn-submit" id="btn-iniciar-prueba">
                        <span>Iniciar Prueba</span>
                    </button>

                </form>
            </div>
        </div>
    </div>

</div>

<script>
    // Auto-uppercase code input
    const codeInput = document.getElementById('codigoSesion');
    if (codeInput) {
        codeInput.addEventListener('input', function() {
            this.value = this.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
        });
    }

    // Subtle form input animations
    document.querySelectorAll('.form-input, .form-select').forEach(function(el) {
        el.addEventListener('focus', function() {
            this.closest('.form-group').style.transform = 'translateY(-1px)';
            this.closest('.form-group').style.transition = 'transform 0.2s ease';
        });
        el.addEventListener('blur', function() {
            this.closest('.form-group').style.transform = 'translateY(0)';
        });
    });
</script>

</body>
</html>
