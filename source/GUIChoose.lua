-- ══════════════════════════════════════════════════════════════
--  RoMidi Loader  |  Loader
--  Launcher — choose Full UI or Compact UI
-- ══════════════════════════════════════════════════════════════

-- ── URLs ──────────────────────────────────────────────────────
local URL_FULL    = "https://gist.githubusercontent.com/VBfHKC86WxpXyIgr/fc61c54dec8188ca691791a09c7effae/raw/RoMidiFull.lua"  .. "?t=" .. tostring(os.time())
local URL_COMPACT = "https://gist.githubusercontent.com/VBfHKC86WxpXyIgr/fb509ff6676f405c21992fba67e48b78/raw/Compact.lua"     .. "?t=" .. tostring(os.time())

-- ── Safe CoreGui ──────────────────────────────────────────────
local function getSafeGui()
    if typeof(gethui) == "function" then return gethui() end
    local ok, cg = pcall(function()
        return cloneref and cloneref(game:GetService("CoreGui")) or game:GetService("CoreGui")
    end)
    if ok and cg then return cg end
    return game:GetService("CoreGui")
end
local CoreGui = getSafeGui()

local function safeDestroy(parent, name)
    local ok, child = pcall(function() return parent:FindFirstChild(name) end)
    if ok and child then pcall(function() child:Destroy() end) end
end

-- ── Services ──────────────────────────────────────────────────
local TweenS = game:GetService("TweenService")
local UIS    = game:GetService("UserInputService")

-- ── Colour palette (mirrors main script) ─────────────────────
local T = {
    bg         = Color3.fromRGB(10,  6,  24),
    panel      = Color3.fromRGB(20, 12,  44),
    card       = Color3.fromRGB(30, 18,  58),
    border     = Color3.fromRGB(90, 55, 170),
    neon       = Color3.fromRGB(155, 80, 255),
    neonB      = Color3.fromRGB(190,110, 255),
    purpleDeep = Color3.fromRGB(45,  15, 100),
    purpleMid  = Color3.fromRGB(85,  35, 175),
    accent     = Color3.fromRGB(215,170, 255),
    neonG      = Color3.fromRGB(80, 220, 150),
    txt        = Color3.fromRGB(230,215, 255),
    txtDim     = Color3.fromRGB(130,105, 180),
    white      = Color3.new(1,1,1),
}

-- ── Scale (capped so the window never exceeds 400×300) ────────
local MAX_W, MAX_H = 400, 300

local Scale = {}
function Scale.init()
    Scale.vp = workspace.CurrentCamera.ViewportSize
    local s  = math.min(Scale.vp.X, Scale.vp.Y)
    Scale.f  = math.max(0.62, math.min(s / 480, 1.0))
end
function Scale.px(n) return math.round(n * Scale.f) end
function Scale.fs(n) return math.max(10, math.round(n * Scale.f)) end
Scale.init()
workspace.CurrentCamera:GetPropertyChangedSignal("ViewportSize"):Connect(Scale.init)

-- ── Tween helpers ─────────────────────────────────────────────
local function tw(obj, t, props, style)
    TweenS:Create(obj, TweenInfo.new(t, style or Enum.EasingStyle.Quad,
        Enum.EasingDirection.Out), props):Play()
end
local function twBack(obj, t, props) tw(obj, t, props, Enum.EasingStyle.Back) end
local function twSine(obj, t, props) tw(obj, t, props, Enum.EasingStyle.Sine) end

-- ── UI factory helpers ────────────────────────────────────────
local UI = {}

function UI.frame(parent, props)
    local f = Instance.new("Frame", parent)
    f.BorderSizePixel = 0
    for k, v in pairs(props or {}) do f[k] = v end
    return f
end

function UI.corner(parent, r)
    local c = Instance.new("UICorner", parent)
    c.CornerRadius = UDim.new(0, r or Scale.px(10))
    return c
end

function UI.stroke(parent, col, thick, trans)
    local s = Instance.new("UIStroke", parent)
    s.Color = col or T.border
    s.Thickness = thick or 1
    s.Transparency = trans or 0
    return s
end

function UI.label(parent, props)
    local l = Instance.new("TextLabel", parent)
    l.BackgroundTransparency = 1
    l.Font       = Enum.Font.BuilderSansBold
    l.TextColor3 = T.txt
    l.BorderSizePixel = 0
    for k, v in pairs(props or {}) do l[k] = v end
    return l
end

function UI.btn(parent, props)
    local b = Instance.new("TextButton", parent)
    b.BorderSizePixel  = 0
    b.Font             = Enum.Font.BuilderSansBold
    b.TextColor3       = T.txt
    b.AutoButtonColor  = false
    for k, v in pairs(props or {}) do b[k] = v end
    return b
end

function UI.ripple(btn, col)
    btn.ClipsDescendants = true
    btn.MouseButton1Down:Connect(function(x, y)
        local rp = UI.frame(btn, {
            Size = UDim2.new(0,0,0,0),
            Position = UDim2.new(0, x - btn.AbsolutePosition.X,
                                 0, y - btn.AbsolutePosition.Y),
            BackgroundColor3    = col or T.white,
            BackgroundTransparency = 0.75,
            ZIndex = btn.ZIndex + 5,
        })
        UI.corner(rp, 999)
        tw(rp, 0.45, { Size = UDim2.new(4,0,4,0), BackgroundTransparency = 1 })
        task.delay(0.5, function() if rp.Parent then rp:Destroy() end end)
    end)
end

function UI.hover(btn, base, hover)
    btn.MouseEnter:Connect(function()  tw(btn, 0.12, { BackgroundColor3 = hover }) end)
    btn.MouseLeave:Connect(function()  tw(btn, 0.12, { BackgroundColor3 = base  }) end)
    UI.ripple(btn, hover)
end

-- ── Dragging ──────────────────────────────────────────────────
local function makeDraggable(handle, target)
    local dragging, dStart, dPos = false, nil, nil
    local dragInput, dragIsMouse = nil, false
    handle.Active = true

    handle.InputBegan:Connect(function(inp)
        if inp.UserInputType == Enum.UserInputType.MouseButton1
        or inp.UserInputType == Enum.UserInputType.Touch then
            if dragging then return end
            dragging = true
            dragInput = inp
            dragIsMouse = (inp.UserInputType == Enum.UserInputType.MouseButton1)
            dStart = inp.Position
            dPos   = target.Position
            inp.Changed:Connect(function()
                if inp.UserInputState == Enum.UserInputState.End and inp == dragInput then
                    dragging = false; dragInput = nil; dragIsMouse = false
                end
            end)
        end
    end)

    local function onMove(inp)
        if not dragging then return end
        local valid = (inp == dragInput)
            or (dragIsMouse and inp.UserInputType == Enum.UserInputType.MouseMovement)
        if valid then
            local d   = inp.Position - dStart
            local vp  = workspace.CurrentCamera.ViewportSize
            local absW = target.AbsoluteSize.X
            local absH = target.AbsoluteSize.Y
            local rawX = dPos.X.Scale * vp.X + dPos.X.Offset + d.X
            local rawY = dPos.Y.Scale * vp.Y + dPos.Y.Offset + d.Y
            target.Position = UDim2.new(0,
                math.clamp(rawX, 0, math.max(0, vp.X - absW)), 0,
                math.clamp(rawY, 0, math.max(0, vp.Y - absH)))
        end
    end

    UIS.InputChanged:Connect(onMove)
    handle.InputChanged:Connect(onMove)
end

-- ══════════════════════════════════════════════════════════════
--  BUILD GUI
-- ══════════════════════════════════════════════════════════════
local W = math.min(MAX_W, Scale.px(400))
local H = math.min(MAX_H, Scale.px(300))

local gui = Instance.new("ScreenGui")
gui.Name               = "RoMidiLoader"
gui.ResetOnSpawn       = false
gui.ZIndexBehavior     = Enum.ZIndexBehavior.Sibling
gui.DisplayOrder       = 999
gui.IgnoreGuiInset     = true
gui.Parent             = CoreGui

-- Recompute window size if viewport changes
workspace.CurrentCamera:GetPropertyChangedSignal("ViewportSize"):Connect(function()
    Scale.init()
    W = math.min(MAX_W, Scale.px(400))
    H = math.min(MAX_H, Scale.px(300))
end)

-- ── Window frame ──────────────────────────────────────────────
local frame = UI.frame(gui, {
    Name             = "Window",
    Size             = UDim2.new(0, W, 0, H),
    Position         = UDim2.new(0.5, -W/2, 0.5, -H/2),
    BackgroundColor3 = T.bg,
    BackgroundTransparency = 1,
    ZIndex           = 10,
})
UI.corner(frame, Scale.px(14))
UI.stroke(frame, T.border, 1.5)

-- Subtle inner gradient overlay
local bgGrad = UI.frame(frame, {
    Size             = UDim2.new(1,0,1,0),
    BackgroundColor3 = T.purpleDeep,
    BackgroundTransparency = 0.72,
    ZIndex           = 10,
})
UI.corner(bgGrad, Scale.px(14))

-- Entry animation
twBack(frame, 0.45, { BackgroundTransparency = 0 })
frame.BackgroundTransparency = 1

-- ── Header bar (draggable) ────────────────────────────────────
local HEADER_H = Scale.px(56)

local header = UI.frame(frame, {
    Name             = "Header",
    Size             = UDim2.new(1, 0, 0, HEADER_H),
    BackgroundColor3 = T.panel,
    ZIndex           = 12,
})
UI.corner(header, Scale.px(14))

-- Square off bottom corners of header
local headerFill = UI.frame(header, {
    Size             = UDim2.new(1,0,0, Scale.px(14)),
    Position         = UDim2.new(0,0,1,-Scale.px(14)),
    BackgroundColor3 = T.panel,
    ZIndex           = 12,
})

-- Header bottom separator line
local sep = UI.frame(frame, {
    Size             = UDim2.new(1, -Scale.px(24), 0, 1),
    Position         = UDim2.new(0, Scale.px(12), 0, HEADER_H),
    BackgroundColor3 = T.border,
    BackgroundTransparency = 0.55,
    ZIndex           = 12,
})

-- ── Music note icon (decorative) ──────────────────────────────
local iconBox = UI.frame(header, {
    Size             = UDim2.new(0, Scale.px(28), 0, Scale.px(28)),
    Position         = UDim2.new(0, Scale.px(14), 0.5, -Scale.px(14)),
    BackgroundColor3 = T.purpleMid,
    ZIndex           = 13,
})
UI.corner(iconBox, Scale.px(7))
UI.stroke(iconBox, T.neon, 1, 0.5)

UI.label(iconBox, {
    Size     = UDim2.new(1,0,1,0),
    Text     = "♪",
    Font     = Enum.Font.BuilderSansBold,
    TextSize = Scale.fs(16),
    TextColor3 = T.neonB,
    ZIndex   = 14,
})

-- ── Title + subtitle ──────────────────────────────────────────
local titleLbl = UI.label(header, {
    Size      = UDim2.new(1, -Scale.px(110), 0, Scale.fs(22)),
    Position  = UDim2.new(0, Scale.px(50), 0, Scale.px(8)),
    Text      = "RoMidi",
    Font      = Enum.Font.BuilderSansBold,
    TextSize  = Scale.fs(20),
    TextColor3 = T.accent,
    TextXAlignment = Enum.TextXAlignment.Left,
    ZIndex    = 13,
})

local subLbl = UI.label(header, {
    Size      = UDim2.new(1, -Scale.px(110), 0, Scale.fs(14)),
    Position  = UDim2.new(0, Scale.px(50), 0, Scale.px(31)),
    Text      = "Loader  ·  Select a UI to load",
    Font      = Enum.Font.BuilderSans,
    TextSize  = Scale.fs(11),
    TextColor3 = T.txtDim,
    TextXAlignment = Enum.TextXAlignment.Left,
    ZIndex    = 13,
})

-- ── Close button ──────────────────────────────────────────────
local closeBtn = UI.btn(header, {
    Size             = UDim2.new(0, Scale.px(26), 0, Scale.px(26)),
    Position         = UDim2.new(1, -Scale.px(38), 0.5, -Scale.px(13)),
    Text             = "✕",
    TextSize         = Scale.fs(12),
    TextColor3       = T.txtDim,
    BackgroundColor3 = T.card,
    ZIndex           = 14,
})
UI.corner(closeBtn, Scale.px(7))
UI.hover(closeBtn, T.card, Color3.fromRGB(200,60,90))

closeBtn.MouseButton1Click:Connect(function()
    tw(frame, 0.25, { BackgroundTransparency = 1, Size = UDim2.new(0, W*0.92, 0, H*0.92) })
    task.delay(0.28, function() gui:Destroy() end)
end)

makeDraggable(header, frame)

-- ══════════════════════════════════════════════════════════════
--  BUTTON PANEL
-- ══════════════════════════════════════════════════════════════
local BODY_Y  = HEADER_H + Scale.px(2)
local BODY_H  = H - BODY_Y
local PAD     = Scale.px(16)
local BTN_H   = Scale.px(90)
local BTN_GAP = Scale.px(10)

-- Total two-button height with gap fits inside body
local function makeUIButton(order, label, desc, icon, baseCol, accentCol, url)
    local totalBtnsH = BTN_H * 2 + BTN_GAP
    local startY = BODY_Y + (BODY_H - totalBtnsH) / 2
    local yOff   = startY + (order - 1) * (BTN_H + BTN_GAP)

    local card = UI.frame(frame, {
        Size             = UDim2.new(1, -PAD*2, 0, BTN_H),
        Position         = UDim2.new(0, PAD, 0, yOff),
        BackgroundColor3 = T.card,
        ZIndex           = 12,
    })
    UI.corner(card, Scale.px(11))
    UI.stroke(card, baseCol, 1, 0.55)

    -- Icon badge
    local badge = UI.frame(card, {
        Size             = UDim2.new(0, Scale.px(36), 0, Scale.px(36)),
        Position         = UDim2.new(0, Scale.px(12), 0.5, -Scale.px(18)),
        BackgroundColor3 = baseCol,
        BackgroundTransparency = 0.35,
        ZIndex           = 13,
    })
    UI.corner(badge, Scale.px(9))
    UI.stroke(badge, accentCol, 1, 0.4)

    UI.label(badge, {
        Size       = UDim2.new(1,0,1,0),
        Text       = icon,
        TextSize   = Scale.fs(18),
        TextColor3 = accentCol,
        ZIndex     = 14,
    })

    -- Button label
    UI.label(card, {
        Size      = UDim2.new(1, -Scale.px(70), 0, Scale.fs(18)),
        Position  = UDim2.new(0, Scale.px(58), 0, Scale.px(14)),
        Text      = label,
        Font      = Enum.Font.BuilderSansBold,
        TextSize  = Scale.fs(15),
        TextColor3 = T.txt,
        TextXAlignment = Enum.TextXAlignment.Left,
        ZIndex    = 13,
    })

    -- Description
    UI.label(card, {
        Size      = UDim2.new(1, -Scale.px(70), 0, Scale.fs(28)),
        Position  = UDim2.new(0, Scale.px(58), 0, Scale.px(34)),
        Text      = desc,
        Font      = Enum.Font.BuilderSans,
        TextSize  = Scale.fs(11),
        TextColor3 = T.txtDim,
        TextXAlignment = Enum.TextXAlignment.Left,
        TextWrapped = true,
        ZIndex    = 13,
    })

    -- Arrow chevron
    UI.label(card, {
        Size      = UDim2.new(0, Scale.px(20), 1, 0),
        Position  = UDim2.new(1, -Scale.px(26), 0, 0),
        Text      = "›",
        Font      = Enum.Font.BuilderSansBold,
        TextSize  = Scale.fs(22),
        TextColor3 = accentCol,
        ZIndex    = 13,
    })

    -- Invisible click overlay
    local hitBox = UI.btn(card, {
        Size             = UDim2.new(1,0,1,0),
        BackgroundTransparency = 1,
        Text             = "",
        ZIndex           = 15,
    })
    UI.corner(hitBox, Scale.px(11))
    UI.ripple(hitBox, accentCol)
    hitBox.MouseEnter:Connect(function()
        tw(card, 0.14, { BackgroundColor3 = T.purpleDeep })
        tw(card, 0.14, {})
        local strk = card:FindFirstChildOfClass("UIStroke")
        if strk then tw(strk, 0.14, { Transparency = 0 }) end
    end)
    hitBox.MouseLeave:Connect(function()
        tw(card, 0.14, { BackgroundColor3 = T.card })
        local strk = card:FindFirstChildOfClass("UIStroke")
        if strk then tw(strk, 0.14, { Transparency = 0.55 }) end
    end)

    hitBox.MouseButton1Click:Connect(function()
        -- Visual feedback: flash the badge accent
        tw(badge, 0.1,  { BackgroundTransparency = 0.1 })
        tw(badge, 0.25, { BackgroundTransparency = 0.35 })

        subLbl.Text = "Loading " .. label .. "…"
        subLbl.TextColor3 = T.neonB

        -- Fade out launcher then load
        task.delay(0.35, function()
            tw(frame, 0.3, { BackgroundTransparency = 1 })
            task.delay(0.35, function()
                safeDestroy(CoreGui, "RoMidi")
                safeDestroy(CoreGui, "RoMidiLoader")
                local ok, err = pcall(function()
                    loadstring(game:HttpGet(url, true))()
                end)
                if not ok then
                    warn("[RoMidiLoader] Failed to load " .. label .. ": " .. tostring(err))
                end
                gui:Destroy()
            end)
        end)
    end)

    -- Slide-in animation staggered per card
    card.Position = UDim2.new(0, PAD + W * 0.08, 0, yOff)
    card.BackgroundTransparency = 1
    task.delay(0.1 + (order - 1) * 0.12, function()
        twBack(card, 0.4, { Position = UDim2.new(0, PAD, 0, yOff), BackgroundTransparency = 0 })
    end)
end

-- ── Run ───────────────────────────────────────────────────────
makeUIButton(1, "Full UI",
    "All tabs, full library browser & controls",
    "⊞", T.purpleMid, T.neonB, URL_FULL)

makeUIButton(2, "Compact UI",
    "Minimal tabs, lighter layout, same core features",
    "▤", Color3.fromRGB(18,68,60), T.neonG, URL_COMPACT)

UI.label(frame, {
    Size      = UDim2.new(1, 0, 0, Scale.fs(12)),
    Position  = UDim2.new(0, 0, 1, -Scale.px(14)),
    Text      = "RoMidi  ·  Loader",
    Font      = Enum.Font.BuilderSans,
    TextSize  = Scale.fs(10),
    TextColor3 = T.txtDim,
    ZIndex    = 12,
})

-- ── Viewport resize: recentre & clamp ─────────────────────────
workspace.CurrentCamera:GetPropertyChangedSignal("ViewportSize"):Connect(function()
    local vp = workspace.CurrentCamera.ViewportSize
    local ap = frame.AbsolutePosition
    local as = frame.AbsoluteSize
    frame.Position = UDim2.new(0,
        math.clamp(ap.X, 0, math.max(0, vp.X - as.X)), 0,
        math.clamp(ap.Y, 0, math.max(0, vp.Y - as.Y)))
end)