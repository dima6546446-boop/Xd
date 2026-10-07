-- // RoMidi Key System
-- // Executor Script — paste into your executor and run

-- ── CHANGE THIS TO YOUR MAIN SCRIPT RAW URL ─────────────────────────────────
local mainscript = "https://gist.githubusercontent.com/VBfHKC86WxpXyIgr/db6cddfb66e46757fe1be3f67e8342e0/raw/GUIChoose.lua"
-- ─────────────────────────────────────────────────────────────────────────────

local KEY_JSON_URL = "https://gist.githubusercontent.com/VBfHKC86WxpXyIgr/6ae3fa80ba16318c88b4c2cb9485bb39/raw/key.json"
local KEY_PAGE     = "https://pandahub-romidi.vercel.app/getkey.html"

local KEY_FOLDER = ".RoMidi"
local KEY_FILE   = KEY_FOLDER .. "/.key"
local TIME_FILE  = KEY_FOLDER .. "/.keytime"
local KEY_TTL    = 86400

local Players      = game:GetService("Players")
local TweenService = game:GetService("TweenService")

local Player    = Players.LocalPlayer
local PlayerGui = Player:WaitForChild("PlayerGui")

-- ── FILE HELPERS ─────────────────────────────────────────────────────────────
local function ensureFolder()
	if not isfolder(KEY_FOLDER) then
		makefolder(KEY_FOLDER)
	end
end

local function saveKeyToDisk(key)
	-- key must come from the TextBox input — never from a URL or any other source
	pcall(function()
		ensureFolder()
		writefile(KEY_FILE,  key)
		writefile(TIME_FILE, tostring(os.time()))  -- exact wall-clock time of save
	end)
end

local function loadKeyFromDisk()
	local ok1, key  = pcall(readfile, KEY_FILE)
	local ok2, stamp = pcall(readfile, TIME_FILE)

	if not ok1 or not ok2 or not key or not stamp then
		return nil
	end

	local savedTime = tonumber(stamp)
	if not savedTime then return nil end

	local elapsed = os.time() - savedTime
	if elapsed > KEY_TTL then
		pcall(writefile, KEY_FILE,  "")
		pcall(writefile, TIME_FILE, "")
		return nil
	end

	local trimmed = key:match("^%s*(.-)%s*$")
	if trimmed == "" then return nil end
	return trimmed
end

-- ── FETCH VALID KEYS ─────────────────────────────────────────────────────────
-- key.txt holds a Lua table: return { keyNow = "ROMIDI-XXXX-XXXX", keyYesterday = "ROMIDI-YYYY-YYYY" }
-- We parse both so a key saved before the daily rotation is still accepted for
-- a full 24 hours after the user first copied it.
local function fetchKeys()
	local ok, raw = pcall(game.HttpGet, game, KEY_JSON_URL)
	if not ok or not raw then return nil, nil, "Failed to fetch key" end

	-- Parse JSON: { "keyNow": "ROMIDI-...", "keyYesterday": "ROMIDI-..." }
	local keyNow       = raw:match('"keyNow"%s*:%s*"([^"]+)"')
	local keyYesterday = raw:match('"keyYesterday"%s*:%s*"([^"]+)"')

	if not keyNow then return nil, nil, "Key data malformed" end

	return keyNow:match("^%s*(.-)%s*$"),
	       keyYesterday and keyYesterday:match("^%s*(.-)%s*$") or nil,
	       nil
end

-- ── LOADING SCREEN ───────────────────────────────────────────────────────────
-- Shows a "Loading Script" UI with an animated dot label while the script URL
-- is being fetched. Destroys itself once loading is complete (or failed).
local function showLoadingScreen(onReady)
	-- Remove any stale loading GUI
	if PlayerGui:FindFirstChild("RoMidiLoading") then
		PlayerGui:FindFirstChild("RoMidiLoading"):Destroy()
	end

	local LoadGui = Instance.new("ScreenGui")
	LoadGui.Name           = "RoMidiLoading"
	LoadGui.ResetOnSpawn   = false
	LoadGui.ZIndexBehavior = Enum.ZIndexBehavior.Sibling
	LoadGui.IgnoreGuiInset = true
	LoadGui.DisplayOrder   = 1000
	LoadGui.Parent         = PlayerGui

	-- Dark overlay
	local Bg = Instance.new("Frame")
	Bg.Size                   = UDim2.fromScale(1, 1)
	Bg.BackgroundColor3       = Color3.fromRGB(5, 0, 15)
	Bg.BackgroundTransparency = 0.1
	Bg.BorderSizePixel        = 0
	Bg.ZIndex                 = 1
	Bg.Parent                 = LoadGui

	-- Floating particles (reuse same style)
	local PF = Instance.new("Frame")
	PF.Size                   = UDim2.fromScale(1, 1)
	PF.BackgroundTransparency = 1
	PF.ZIndex                 = 2
	PF.Parent                 = LoadGui

	for i = 1, 30 do
		local p = Instance.new("Frame")
		p.Size             = UDim2.fromOffset(math.random(2, 5), math.random(2, 5))
		p.Position         = UDim2.fromScale(math.random(), math.random())
		p.BackgroundColor3 = math.random() > 0.5
			and Color3.fromRGB(139, 92, 246)
			or  Color3.fromRGB(245, 158, 11)
		p.BackgroundTransparency = math.random(45, 80) / 100
		p.BorderSizePixel  = 0
		p.ZIndex           = 2
		p.Parent           = PF
		Instance.new("UICorner", p).CornerRadius = UDim.new(1, 0)

		local spd   = math.random(18, 50)
		local drift = math.random(-15, 15) / 1000
		local sy    = p.Position.Y.Scale
		task.spawn(function()
			while p.Parent do
				local t = TweenService:Create(p,
					TweenInfo.new(spd, Enum.EasingStyle.Linear),
					{ Position = UDim2.fromScale(p.Position.X.Scale + drift * spd, sy - 0.85) })
				t:Play(); t.Completed:Wait()
				p.Position = UDim2.fromScale(math.random(), 1.05)
				sy = 1.05
			end
		end)
	end

	-- Card
	local Card = Instance.new("Frame")
	Card.Name                = "LoadCard"
	Card.Size                = UDim2.fromOffset(300, 160)
	Card.AnchorPoint         = Vector2.new(0.5, 0.5)
	Card.Position            = UDim2.fromScale(0.5, 0.5)
	Card.BackgroundColor3    = Color3.fromRGB(18, 6, 38)
	Card.BackgroundTransparency = 1   -- starts invisible for intro tween
	Card.BorderSizePixel     = 0
	Card.ZIndex              = 5
	Card.Parent              = LoadGui
	Instance.new("UICorner", Card).CornerRadius = UDim.new(0, 22)

	local CardStroke = Instance.new("UIStroke")
	CardStroke.Color        = Color3.fromRGB(109, 40, 217)
	CardStroke.Thickness    = 1.5
	CardStroke.Transparency = 0.35
	CardStroke.Parent       = Card

	-- Animated gradient top bar
	local TopBar = Instance.new("Frame")
	TopBar.Size             = UDim2.new(1, 0, 0, 3)
	TopBar.BackgroundColor3 = Color3.fromRGB(139, 92, 246)
	TopBar.BorderSizePixel  = 0
	TopBar.ZIndex           = 6
	TopBar.Parent           = Card
	Instance.new("UICorner", TopBar).CornerRadius = UDim.new(0, 22)

	local TopGrad = Instance.new("UIGradient")
	TopGrad.Color = ColorSequence.new({
		ColorSequenceKeypoint.new(0,   Color3.fromRGB(109, 40, 217)),
		ColorSequenceKeypoint.new(0.5, Color3.fromRGB(245, 158, 11)),
		ColorSequenceKeypoint.new(1,   Color3.fromRGB(6, 182, 212)),
	})
	TopGrad.Parent = TopBar

	-- Spinner icon label
	local SpinnerLabel = Instance.new("TextLabel")
	SpinnerLabel.Size                   = UDim2.new(1, 0, 0, 36)
	SpinnerLabel.AnchorPoint            = Vector2.new(0.5, 0)
	SpinnerLabel.Position               = UDim2.new(0.5, 0, 0, 22)
	SpinnerLabel.BackgroundTransparency = 1
	SpinnerLabel.Text                   = "⏳"
	SpinnerLabel.Font                   = Enum.Font.GothamBold
	SpinnerLabel.TextScaled             = true
	SpinnerLabel.ZIndex                 = 6
	SpinnerLabel.Parent                 = Card

	-- Title
	local TitleLabel = Instance.new("TextLabel")
	TitleLabel.Size                   = UDim2.new(1, -30, 0, 22)
	TitleLabel.AnchorPoint            = Vector2.new(0.5, 0)
	TitleLabel.Position               = UDim2.new(0.5, 0, 0, 68)
	TitleLabel.BackgroundTransparency = 1
	TitleLabel.Text                   = "Loading Script"
	TitleLabel.Font                   = Enum.Font.GothamBold
	TitleLabel.TextSize               = 16
	TitleLabel.TextColor3             = Color3.fromRGB(245, 240, 255)
	TitleLabel.TextXAlignment         = Enum.TextXAlignment.Center
	TitleLabel.ZIndex                 = 6
	TitleLabel.Parent                 = Card

	-- Animated status label — shows "Loading" with cycling dots while fetching
	local StatusLabel = Instance.new("TextLabel")
	StatusLabel.Size                   = UDim2.new(1, -30, 0, 18)
	StatusLabel.AnchorPoint            = Vector2.new(0.5, 0)
	StatusLabel.Position               = UDim2.new(0.5, 0, 0, 96)
	StatusLabel.BackgroundTransparency = 1
	StatusLabel.Text                   = "Loading"
	StatusLabel.Font                   = Enum.Font.Gotham
	StatusLabel.TextSize               = 12
	StatusLabel.TextColor3             = Color3.fromRGB(139, 92, 246)
	StatusLabel.TextXAlignment         = Enum.TextXAlignment.Center
	StatusLabel.ZIndex                 = 6
	StatusLabel.Parent                 = Card

	-- Progress bar track
	local BarBg = Instance.new("Frame")
	BarBg.Size             = UDim2.new(1, -40, 0, 4)
	BarBg.AnchorPoint      = Vector2.new(0.5, 0)
	BarBg.Position         = UDim2.new(0.5, 0, 0, 126)
	BarBg.BackgroundColor3 = Color3.fromRGB(40, 20, 70)
	BarBg.BorderSizePixel  = 0
	BarBg.ZIndex           = 6
	BarBg.Parent           = Card
	Instance.new("UICorner", BarBg).CornerRadius = UDim.new(1, 0)

	-- Progress bar fill (animated shimmer while loading)
	local BarFill = Instance.new("Frame")
	BarFill.Size             = UDim2.fromScale(0, 1)
	BarFill.BackgroundColor3 = Color3.fromRGB(139, 92, 246)
	BarFill.BorderSizePixel  = 0
	BarFill.ZIndex           = 7
	BarFill.Parent           = BarBg
	Instance.new("UICorner", BarFill).CornerRadius = UDim.new(1, 0)

	local BarGrad = Instance.new("UIGradient")
	BarGrad.Color = ColorSequence.new({
		ColorSequenceKeypoint.new(0, Color3.fromRGB(109, 40, 217)),
		ColorSequenceKeypoint.new(1, Color3.fromRGB(245, 158, 11)),
	})
	BarGrad.Parent = BarFill

	-- Intro pop-in animation
	Card.Size = UDim2.fromOffset(0, 0)
	TweenService:Create(Card,
		TweenInfo.new(0.45, Enum.EasingStyle.Back, Enum.EasingDirection.Out),
		{ Size = UDim2.fromOffset(300, 160), BackgroundTransparency = 0 }
	):Play()

	-- Gradient bar rotation
	task.spawn(function()
		local r = 0
		while Card.Parent do
			r = (r + 0.4) % 360
			TopGrad.Rotation = r
			task.wait(0.03)
		end
	end)

	-- Dot animation while loading (Loading → Loading. → Loading.. → Loading...)
	local dotConn
	local dotCount = 0
	local dotFrames = { "Loading", "Loading.", "Loading..", "Loading..." }
	local alive = true
	task.spawn(function()
		while alive and StatusLabel.Parent do
			dotCount = (dotCount % 4) + 1
			StatusLabel.Text = dotFrames[dotCount]
			task.wait(0.45)
		end
	end)

	-- Shimmer the progress bar back and forth while waiting
	task.spawn(function()
		while alive and BarFill.Parent do
			TweenService:Create(BarFill, TweenInfo.new(0.9, Enum.EasingStyle.Sine, Enum.EasingDirection.InOut),
				{ Size = UDim2.fromScale(0.85, 1) }):Play()
			task.wait(0.9)
			TweenService:Create(BarFill, TweenInfo.new(0.9, Enum.EasingStyle.Sine, Enum.EasingDirection.InOut),
				{ Size = UDim2.fromScale(0.15, 1) }):Play()
			task.wait(0.9)
		end
	end)

	-- This function is called when the script finishes loading (success or fail)
	local function finishLoading(success)
		alive = false  -- stop dot + shimmer loops

		if success then
			-- Fill bar to full and turn green
			TweenService:Create(BarFill, TweenInfo.new(0.3),
				{ Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.fromRGB(74, 222, 128) }):Play()
			TweenService:Create(Card, TweenInfo.new(0.3),
				{ BackgroundColor3 = Color3.fromRGB(10, 30, 18) }):Play()
			TweenService:Create(CardStroke, TweenInfo.new(0.3),
				{ Color = Color3.fromRGB(74, 222, 128), Transparency = 0 }):Play()
			SpinnerLabel.Text  = "✓"
			StatusLabel.Text   = "Done!"
			StatusLabel.TextColor3 = Color3.fromRGB(74, 222, 128)
		else
			TweenService:Create(CardStroke, TweenInfo.new(0.3),
				{ Color = Color3.fromRGB(239, 68, 68), Transparency = 0 }):Play()
			SpinnerLabel.Text  = "✗"
			StatusLabel.Text   = "Failed to load"
			StatusLabel.TextColor3 = Color3.fromRGB(239, 68, 68)
		end

		task.wait(0.8)

		-- Dismiss loading card
		TweenService:Create(Card,
			TweenInfo.new(0.4, Enum.EasingStyle.Back, Enum.EasingDirection.In),
			{ Size = UDim2.fromOffset(0, 0), BackgroundTransparency = 1 }):Play()
		TweenService:Create(Bg, TweenInfo.new(0.5),
			{ BackgroundTransparency = 1 }):Play()

		task.wait(0.55)
		LoadGui:Destroy()
	end

	-- Hand back the finish callback so the caller can trigger it
	if onReady then
		onReady(finishLoading)
	end
end

-- ── LOAD MAIN SCRIPT ─────────────────────────────────────────────────────────
-- onDone(success: boolean) is called once the fetch + compile step is resolved.
local function loadMain(onDone)
	local ok, src = pcall(game.HttpGet, game, mainscript)
	if not ok or not src then
		warn("[RoMidi] Failed to load main script: " .. tostring(src))
		if onDone then onDone(false) end
		return
	end
	local fn, err = loadstring(src)
	if not fn then
		warn("[RoMidi] Compile error: " .. tostring(err))
		if onDone then onDone(false) end
		return
	end
	if onDone then onDone(true) end
	task.spawn(fn)
end

-- ── AUTO-BYPASS: validate saved key against gist before skipping GUI ─────────
-- The saved key must still match keyNow OR keyYesterday on the gist.
-- If it matches neither, the key was modified/tampered — clear it and warn.
-- TTL is always 24 hours from the exact moment the user saved it.
local _startupWarning = nil

do
	local cachedKey = loadKeyFromDisk()  -- returns nil if expired or missing
	if cachedKey then
		-- Fetch live keys to validate the saved one
		local keyNow, keyYesterday, fetchErr = fetchKeys()

		if not keyNow then
			-- Network error — can't validate; clear cache and fall through to GUI
			pcall(writefile, KEY_FILE,  "")
			pcall(writefile, TIME_FILE, "")
			_startupWarning = "⚠  Could not verify saved key. Please re-enter."

		elseif cachedKey == keyNow or (keyYesterday and cachedKey == keyYesterday) then
			-- ✅ Saved key still valid — skip GUI entirely
			showLoadingScreen(function(finish)
				task.spawn(function()
					loadMain(finish)
				end)
			end)
			return

		else
			-- ❌ Saved key doesn't match either gist key — it was modified
			pcall(writefile, KEY_FILE,  "")
			pcall(writefile, TIME_FILE, "")
			_startupWarning = "⚠  Saved key was modified and is no longer valid."
		end
	end
end

-- ── DESTROY OLD GUI ───────────────────────────────────────────────────────────
if PlayerGui:FindFirstChild("RoMidiKeySystem") then
	PlayerGui:FindFirstChild("RoMidiKeySystem"):Destroy()
end

-- ── GUI BUILD ────────────────────────────────────────────────────────────────
local ScreenGui = Instance.new("ScreenGui")
ScreenGui.Name             = "RoMidiKeySystem"
ScreenGui.ResetOnSpawn     = false
ScreenGui.ZIndexBehavior   = Enum.ZIndexBehavior.Sibling
ScreenGui.IgnoreGuiInset   = true
ScreenGui.DisplayOrder     = 999
ScreenGui.Parent           = PlayerGui

-- Overlay
local Overlay = Instance.new("Frame")
Overlay.Size                   = UDim2.fromScale(1, 1)
Overlay.BackgroundColor3       = Color3.fromRGB(5, 0, 15)
Overlay.BackgroundTransparency = 0.18
Overlay.BorderSizePixel        = 0
Overlay.ZIndex                 = 1
Overlay.Parent                 = ScreenGui

-- Particles
local PF = Instance.new("Frame")
PF.Size                   = UDim2.fromScale(1, 1)
PF.BackgroundTransparency = 1
PF.ZIndex                 = 2
PF.Parent                 = ScreenGui

for i = 1, 40 do
	local p = Instance.new("Frame")
	p.Size             = UDim2.fromOffset(math.random(2, 5), math.random(2, 5))
	p.Position         = UDim2.fromScale(math.random(), math.random())
	p.BackgroundColor3 = math.random() > 0.5
		and Color3.fromRGB(139, 92, 246)
		or  Color3.fromRGB(245, 158, 11)
	p.BackgroundTransparency = math.random(45, 80) / 100
	p.BorderSizePixel  = 0
	p.ZIndex           = 2
	p.Parent           = PF
	Instance.new("UICorner", p).CornerRadius = UDim.new(1, 0)

	local spd   = math.random(18, 50)
	local drift = math.random(-15, 15) / 1000
	local sy    = p.Position.Y.Scale
	task.spawn(function()
		while p.Parent do
			local t = TweenService:Create(p,
				TweenInfo.new(spd, Enum.EasingStyle.Linear),
				{ Position = UDim2.fromScale(p.Position.X.Scale + drift * spd, sy - 0.85) })
			t:Play(); t.Completed:Wait()
			p.Position = UDim2.fromScale(math.random(), 1.05)
			sy = 1.05
		end
	end)
end

-- Card
local Card = Instance.new("Frame")
Card.Name                = "Card"
Card.Size                = UDim2.fromOffset(380, 318)
Card.AnchorPoint         = Vector2.new(0.5, 0.5)
Card.Position            = UDim2.fromScale(0.5, 0.5)
Card.BackgroundColor3    = Color3.fromRGB(18, 6, 38)
Card.BorderSizePixel     = 0
Card.ZIndex              = 5
Card.Parent              = ScreenGui
Instance.new("UICorner", Card).CornerRadius = UDim.new(0, 22)

local CardStroke = Instance.new("UIStroke")
CardStroke.Color        = Color3.fromRGB(109, 40, 217)
CardStroke.Thickness    = 1.5
CardStroke.Transparency = 0.35
CardStroke.Parent       = Card

-- Top gradient bar
local TopBar = Instance.new("Frame")
TopBar.Size             = UDim2.new(1, 0, 0, 3)
TopBar.BackgroundColor3 = Color3.fromRGB(139, 92, 246)
TopBar.BorderSizePixel  = 0
TopBar.ZIndex           = 6
TopBar.Parent           = Card
Instance.new("UICorner", TopBar).CornerRadius = UDim.new(0, 22)

local TopGrad = Instance.new("UIGradient")
TopGrad.Color = ColorSequence.new({
	ColorSequenceKeypoint.new(0,   Color3.fromRGB(109, 40, 217)),
	ColorSequenceKeypoint.new(0.5, Color3.fromRGB(245, 158, 11)),
	ColorSequenceKeypoint.new(1,   Color3.fromRGB(6, 182, 212)),
})
TopGrad.Parent = TopBar

-- Emblem
local EF = Instance.new("Frame")
EF.Size             = UDim2.fromOffset(56, 56)
EF.AnchorPoint      = Vector2.new(0.5, 0)
EF.Position         = UDim2.new(0.5, 0, 0, 14)
EF.BackgroundColor3 = Color3.fromRGB(30, 10, 60)
EF.BorderSizePixel  = 0
EF.ZIndex           = 6
EF.Parent           = Card
Instance.new("UICorner", EF).CornerRadius = UDim.new(1, 0)

local ES = Instance.new("UIStroke")
ES.Color        = Color3.fromRGB(139, 92, 246)
ES.Thickness    = 1.5
ES.Transparency = 0.2
ES.Parent       = EF

local EI = Instance.new("TextLabel")
EI.Size                   = UDim2.fromScale(1, 1)
EI.BackgroundTransparency = 1
EI.Text                   = "🔑"
EI.Font                   = Enum.Font.GothamBold
EI.TextScaled             = true
EI.ZIndex                 = 7
EI.Parent                 = EF

-- Title
local Title = Instance.new("TextLabel")
Title.Size                   = UDim2.new(1, -40, 0, 26)
Title.AnchorPoint            = Vector2.new(0.5, 0)
Title.Position               = UDim2.new(0.5, 0, 0, 80)
Title.BackgroundTransparency = 1
Title.Text                   = "RoMidi Key System"
Title.Font                   = Enum.Font.GothamBold
Title.TextSize               = 18
Title.TextColor3             = Color3.fromRGB(245, 240, 255)
Title.TextXAlignment         = Enum.TextXAlignment.Center
Title.ZIndex                 = 6
Title.Parent                 = Card

-- Subtitle
local Sub = Instance.new("TextLabel")
Sub.Size                   = UDim2.new(1, -60, 0, 16)
Sub.AnchorPoint            = Vector2.new(0.5, 0)
Sub.Position               = UDim2.new(0.5, 0, 0, 110)
Sub.BackgroundTransparency = 1
Sub.Text                   = "Enter your key to load the script"
Sub.Font                   = Enum.Font.Gotham
Sub.TextSize               = 11
Sub.TextColor3             = Color3.fromRGB(139, 92, 246)
Sub.TextXAlignment         = Enum.TextXAlignment.Center
Sub.ZIndex                 = 6
Sub.Parent                 = Card

-- Input box background
local InputBg = Instance.new("Frame")
InputBg.Size             = UDim2.new(1, -50, 0, 42)
InputBg.AnchorPoint      = Vector2.new(0.5, 0)
InputBg.Position         = UDim2.new(0.5, 0, 0, 138)
InputBg.BackgroundColor3 = Color3.fromRGB(10, 4, 25)
InputBg.BorderSizePixel  = 0
InputBg.ZIndex           = 6
InputBg.Parent           = Card
Instance.new("UICorner", InputBg).CornerRadius = UDim.new(0, 12)

local InputStroke = Instance.new("UIStroke")
InputStroke.Color        = Color3.fromRGB(80, 40, 140)
InputStroke.Thickness    = 1
InputStroke.Transparency = 0.2
InputStroke.Parent       = InputBg

-- Key input field
local KeyInput = Instance.new("TextBox")
KeyInput.Size                   = UDim2.new(1, -20, 1, 0)
KeyInput.AnchorPoint            = Vector2.new(0.5, 0.5)
KeyInput.Position               = UDim2.fromScale(0.5, 0.5)
KeyInput.BackgroundTransparency = 1
KeyInput.PlaceholderText        = "ROMIDI-XXXX-XXXX"
KeyInput.PlaceholderColor3      = Color3.fromRGB(80, 60, 110)
KeyInput.Text                   = ""
KeyInput.Font                   = Enum.Font.GothamMedium
KeyInput.TextSize               = 13
KeyInput.TextColor3             = Color3.fromRGB(245, 240, 255)
KeyInput.ClearTextOnFocus       = false
KeyInput.ZIndex                 = 7
KeyInput.Parent                 = InputBg

-- Status label
local StatusLabel = Instance.new("TextLabel")
StatusLabel.Size                   = UDim2.new(1, -40, 0, 16)
StatusLabel.AnchorPoint            = Vector2.new(0.5, 0)
StatusLabel.Position               = UDim2.new(0.5, 0, 0, 188)
StatusLabel.BackgroundTransparency = 1
StatusLabel.Text                   = ""
StatusLabel.Font                   = Enum.Font.Gotham
StatusLabel.TextSize               = 11
StatusLabel.TextColor3             = Color3.fromRGB(109, 80, 160)
StatusLabel.TextXAlignment         = Enum.TextXAlignment.Center
StatusLabel.ZIndex                 = 6
StatusLabel.Parent                 = Card

-- Verify Button
local VBtn = Instance.new("TextButton")
VBtn.Size             = UDim2.new(1, -50, 0, 40)
VBtn.AnchorPoint      = Vector2.new(0.5, 0)
VBtn.Position         = UDim2.new(0.5, 0, 0, 208)
VBtn.BackgroundColor3 = Color3.fromRGB(109, 40, 217)
VBtn.BorderSizePixel  = 0
VBtn.Text             = "⚡  Verify Key"
VBtn.Font             = Enum.Font.GothamBold
VBtn.TextSize         = 13
VBtn.TextColor3       = Color3.fromRGB(245, 240, 255)
VBtn.AutoButtonColor  = false
VBtn.ZIndex           = 6
VBtn.Parent           = Card
Instance.new("UICorner", VBtn).CornerRadius = UDim.new(0, 12)

local VGrad = Instance.new("UIGradient")
VGrad.Color = ColorSequence.new({
	ColorSequenceKeypoint.new(0, Color3.fromRGB(91, 33, 182)),
	ColorSequenceKeypoint.new(1, Color3.fromRGB(139, 92, 246)),
})
VGrad.Rotation = 90
VGrad.Parent = VBtn

-- Get Key Button
local GKBtn = Instance.new("TextButton")
GKBtn.Size             = UDim2.new(1, -50, 0, 34)
GKBtn.AnchorPoint      = Vector2.new(0.5, 0)
GKBtn.Position         = UDim2.new(0.5, 0, 0, 256)
GKBtn.BackgroundColor3 = Color3.fromRGB(15, 8, 32)
GKBtn.BorderSizePixel  = 0
GKBtn.Text             = "🔗  Get Key  —  copy link to clipboard"
GKBtn.Font             = Enum.Font.Gotham
GKBtn.TextSize         = 11
GKBtn.TextColor3       = Color3.fromRGB(139, 92, 246)
GKBtn.AutoButtonColor  = false
GKBtn.ZIndex           = 6
GKBtn.Parent           = Card
Instance.new("UICorner", GKBtn).CornerRadius = UDim.new(0, 10)

local GKStroke = Instance.new("UIStroke")
GKStroke.Color        = Color3.fromRGB(80, 40, 140)
GKStroke.Thickness    = 1
GKStroke.Transparency = 0.4
GKStroke.Parent       = GKBtn

-- ── HELPERS ──────────────────────────────────────────────────────────────────
local function setStatus(msg, color)
	StatusLabel.Text       = msg
	StatusLabel.TextColor3 = color or Color3.fromRGB(109, 80, 160)
end

local function pulseStroke(color)
	local tIn  = TweenService:Create(CardStroke, TweenInfo.new(0.3),
		{ Color = color, Transparency = 0 })
	local tOut = TweenService:Create(CardStroke, TweenInfo.new(0.7),
		{ Color = Color3.fromRGB(109, 40, 217), Transparency = 0.35 })
	tIn:Play()
	tIn.Completed:Connect(function() tOut:Play() end)
end

local function shakeCard()
	local o = Card.Position
	for _, v in ipairs({10, -10, 7, -7, 4, -4, 0}) do
		Card.Position = UDim2.new(o.X.Scale, v, o.Y.Scale, 0)
		task.wait(0.04)
	end
	Card.Position = o
end

-- Dismisses the key card, then opens the loading screen and fires loadMain.
local function dismissAndLoad()
	TweenService:Create(Card, TweenInfo.new(0.3),
		{ BackgroundColor3 = Color3.fromRGB(10, 30, 18) }):Play()
	TweenService:Create(CardStroke, TweenInfo.new(0.3),
		{ Color = Color3.fromRGB(74, 222, 128), Transparency = 0 }):Play()

	task.wait(0.9)

	-- Shrink and fade the key card out
	TweenService:Create(Card,
		TweenInfo.new(0.45, Enum.EasingStyle.Back, Enum.EasingDirection.In),
		{ Size = UDim2.fromOffset(0, 0), BackgroundTransparency = 1 }):Play()
	TweenService:Create(Overlay, TweenInfo.new(0.55),
		{ BackgroundTransparency = 1 }):Play()

	task.wait(0.5)
	ScreenGui:Destroy()

	-- 🚀 Show loading screen, then fetch + execute main script
	showLoadingScreen(function(finish)
		task.spawn(function()
			loadMain(finish)
		end)
	end)
end

-- ── LIVE FORMAT GLOW ─────────────────────────────────────────────────────────
KeyInput:GetPropertyChangedSignal("Text"):Connect(function()
	local t = KeyInput.Text:match("^%s*(.-)%s*$")
	if t:match("^ROMIDI%-%w+%-%w+$") then
		TweenService:Create(VBtn, TweenInfo.new(0.2),
			{ TextColor3 = Color3.fromRGB(253, 224, 71) }):Play()
		TweenService:Create(VBtn, TweenInfo.new(0.2),
			{ BackgroundColor3 = Color3.fromRGB(124, 58, 237) }):Play()
		TweenService:Create(CardStroke, TweenInfo.new(0.25),
			{ Color = Color3.fromRGB(139, 92, 246), Transparency = 0.05 }):Play()
	else
		TweenService:Create(VBtn, TweenInfo.new(0.2),
			{ TextColor3 = Color3.fromRGB(180, 165, 210) }):Play()
		TweenService:Create(VBtn, TweenInfo.new(0.2),
			{ BackgroundColor3 = Color3.fromRGB(109, 40, 217) }):Play()
		TweenService:Create(CardStroke, TweenInfo.new(0.25),
			{ Color = Color3.fromRGB(109, 40, 217), Transparency = 0.35 }):Play()
	end
end)

-- ── INPUT FOCUS ───────────────────────────────────────────────────────────────
KeyInput.Focused:Connect(function()
	TweenService:Create(InputStroke, TweenInfo.new(0.2),
		{ Color = Color3.fromRGB(139, 92, 246), Transparency = 0 }):Play()
end)
KeyInput.FocusLost:Connect(function()
	TweenService:Create(InputStroke, TweenInfo.new(0.2),
		{ Color = Color3.fromRGB(80, 40, 140), Transparency = 0.2 }):Play()
end)

-- ── VERIFY BUTTON ─────────────────────────────────────────────────────────────
VBtn.MouseEnter:Connect(function()
	TweenService:Create(VBtn, TweenInfo.new(0.15),
		{ BackgroundColor3 = Color3.fromRGB(124, 58, 237) }):Play()
end)
VBtn.MouseLeave:Connect(function()
	TweenService:Create(VBtn, TweenInfo.new(0.15),
		{ BackgroundColor3 = Color3.fromRGB(109, 40, 217) }):Play()
end)

local verifying = false
VBtn.MouseButton1Click:Connect(function()
	if verifying then return end
	local input = KeyInput.Text:match("^%s*(.-)%s*$")

	if input == "" then
		setStatus("⚠  Please enter your key", Color3.fromRGB(245, 158, 11))
		shakeCard()
		return
	end

	if not input:match("^ROMIDI%-%w+%-%w+$") then
		setStatus("✗  Invalid key format", Color3.fromRGB(239, 68, 68))
		pulseStroke(Color3.fromRGB(239, 68, 68))
		shakeCard()
		return
	end

	verifying = true
	VBtn.Text   = "  Verifying…"
	VBtn.Active = false
	setStatus("Fetching key from server…", Color3.fromRGB(139, 92, 246))

	task.spawn(function()
		local keyNow, keyYesterday, err = fetchKeys()

		if not keyNow then
			setStatus("✗  " .. (err or "Network error"), Color3.fromRGB(239, 68, 68))
			pulseStroke(Color3.fromRGB(239, 68, 68))
			shakeCard()
			VBtn.Text   = "⚡  Verify Key"
			VBtn.Active = true
			verifying   = false
			return
		end

		-- Accept keyNow OR keyYesterday so a key copied before the daily
		-- rotation is still valid for the user's full personal 24-hour window.
		local isValid = (input == keyNow) or (keyYesterday and input == keyYesterday)

		if isValid then
			-- ✅ Save key + timestamp to disk for 24-hour bypass
			saveKeyToDisk(input)

			setStatus("✓  Key accepted! Loading script…", Color3.fromRGB(74, 222, 128))
			VBtn.Text = "✓  Verified!"
			task.spawn(dismissAndLoad)
		else
			setStatus("✗  Wrong key. Click Get Key below.", Color3.fromRGB(239, 68, 68))
			pulseStroke(Color3.fromRGB(239, 68, 68))
			shakeCard()
			VBtn.Text   = "⚡  Verify Key"
			VBtn.Active = true
			verifying   = false
		end
	end)
end)

-- ── GET KEY BUTTON ────────────────────────────────────────────────────────────
GKBtn.MouseEnter:Connect(function()
	TweenService:Create(GKBtn, TweenInfo.new(0.15),
		{ BackgroundColor3 = Color3.fromRGB(25, 12, 50) }):Play()
	TweenService:Create(GKStroke, TweenInfo.new(0.15),
		{ Transparency = 0 }):Play()
end)
GKBtn.MouseLeave:Connect(function()
	TweenService:Create(GKBtn, TweenInfo.new(0.15),
		{ BackgroundColor3 = Color3.fromRGB(15, 8, 32) }):Play()
	TweenService:Create(GKStroke, TweenInfo.new(0.15),
		{ Transparency = 0.4 }):Play()
end)

GKBtn.MouseButton1Click:Connect(function()
	setclipboard(KEY_PAGE)
	local orig = GKBtn.Text
	GKBtn.Text       = "✓  Link copied! Paste it in your browser"
	GKBtn.TextColor3 = Color3.fromRGB(74, 222, 128)
	task.delay(2.5, function()
		if GKBtn and GKBtn.Parent then
			GKBtn.Text       = orig
			GKBtn.TextColor3 = Color3.fromRGB(139, 92, 246)
		end
	end)
end)

-- ── INTRO ANIMATION ───────────────────────────────────────────────────────────
Card.Size                   = UDim2.fromOffset(0, 0)
Card.BackgroundTransparency = 1

TweenService:Create(Card,
	TweenInfo.new(0.55, Enum.EasingStyle.Back, Enum.EasingDirection.Out),
	{ Size = UDim2.fromOffset(380, 318), BackgroundTransparency = 0 }
):Play()

-- If the saved key failed gist validation, show the warning once the card is visible
if _startupWarning then
	task.delay(0.65, function()
		if StatusLabel and StatusLabel.Parent then
			setStatus(_startupWarning, Color3.fromRGB(245, 158, 11))
			pulseStroke(Color3.fromRGB(245, 158, 11))
		end
	end)
end

task.spawn(function()
	local r = 0
	while Card.Parent do
		r = (r + 0.4) % 360
		TopGrad.Rotation = r
		task.wait(0.03)
	end
end)