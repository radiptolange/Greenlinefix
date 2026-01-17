import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const AmoledFixApp());
}

class AmoledFixApp extends StatelessWidget {
  const AmoledFixApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'AMOLED Fix',
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF121212),
        primaryColor: Colors.tealAccent,
        sliderTheme: const SliderThemeData(
          activeTrackColor: Colors.tealAccent,
          thumbColor: Colors.tealAccent,
        ),
      ),
      home: const DashboardScreen(),
    );
  }
}

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({Key? key}) : super(key: key);

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> with WidgetsBindingObserver {
  static const platform = MethodChannel('com.example.amoled_fix/overlay');

  bool _isOverlayActive = false;
  double _lineWidth = 5.0;
  bool _hasPermission = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkPermission();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkPermission();
    }
  }

  Future<void> _checkPermission() async {
    // Check overlay permission via permission_handler
    final bool hasPerm = await Permission.systemAlertWindow.isGranted;
    setState(() {
      _hasPermission = hasPerm;
    });
  }

  Future<void> _requestPermission() async {
    await Permission.systemAlertWindow.request();
    // The user goes to settings. When they return, didChangeAppLifecycleState will trigger check.
  }

  Future<void> _toggleOverlay() async {
    if (!_hasPermission) {
      // Double check in case it changed
      await _checkPermission();
      if (!_hasPermission) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text("Permission required first!")),
          );
          return;
      }
    }

    try {
      if (_isOverlayActive) {
        await platform.invokeMethod('stopOverlay');
      } else {
        await platform.invokeMethod('startOverlay');
      }
      setState(() {
        _isOverlayActive = !_isOverlayActive;
      });
    } on PlatformException catch (e) {
      debugPrint("Error: ${e.message}");
      if (e.code == "PERM_DENIED") {
         _checkPermission(); // Sync state
         ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text("Permission denied by system.")),
          );
      }
    }
  }

  Future<void> _addLine() async {
    if (!_isOverlayActive) return;
    await platform.invokeMethod('addLine');
  }

  Future<void> _updateWidth(double val) async {
    setState(() {
      _lineWidth = val;
    });
    // Debouncing could be added here for performance
    if (_isOverlayActive) {
      await platform.invokeMethod('updateWidth', {'width': val.toInt()});
    }
  }

  Future<void> _saveProfile() async {
    if (!_isOverlayActive) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Activate overlay first")),
      );
      return;
    }

    try {
      final result = await platform.invokeMethod('getLines');
      final lines = List<Map<dynamic, dynamic>>.from(result);

      final nameController = TextEditingController();
      final name = await showDialog<String>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text("Save Profile"),
          content: TextField(
            controller: nameController,
            decoration: const InputDecoration(hintText: "Profile Name"),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text("Cancel"),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, nameController.text),
              child: const Text("Save"),
            ),
          ],
        ),
      );

      if (name != null && name.isNotEmpty) {
        final prefs = await SharedPreferences.getInstance();
        final profilesJson = prefs.getString('profiles');
        Map<String, dynamic> profiles = profilesJson != null ? jsonDecode(profilesJson) : {};

        profiles[name] = lines;
        await prefs.setString('profiles', jsonEncode(profiles));

        if (mounted) {
           ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text("Profile '$name' saved")),
          );
        }
      }
    } catch (e) {
      debugPrint("Error saving profile: $e");
       ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error saving profile: $e")),
      );
    }
  }

  Future<void> _loadProfile() async {
    final prefs = await SharedPreferences.getInstance();
    final profilesJson = prefs.getString('profiles');
    if (profilesJson == null) {
       ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("No profiles saved")),
      );
      return;
    }

    Map<String, dynamic> profiles = jsonDecode(profilesJson);
    if (profiles.isEmpty) {
       ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("No profiles saved")),
      );
      return;
    }

    final selectedProfile = await showDialog<String>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text("Select Profile"),
        children: profiles.keys.map((name) {
          return SimpleDialogOption(
            onPressed: () => Navigator.pop(context, name),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8.0),
              child: Text(name),
            ),
          );
        }).toList(),
      ),
    );

    if (selectedProfile != null) {
      if (!_isOverlayActive) {
         // Try to start it
         await _toggleOverlay();
         // Wait a bit? logic is in _toggleOverlay
         if (!_isOverlayActive) return; // Failed to start
      }

      try {
        List<dynamic> lines = profiles[selectedProfile];
        await platform.invokeMethod('setLines', {'lines': lines});
        if (mounted) {
           ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text("Profile '$selectedProfile' loaded")),
          );
        }
      } catch (e) {
        debugPrint("Error loading profile: $e");
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("AMOLED Patcher"),
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 1. Permission Card
            if (!_hasPermission)
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.red.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.redAccent),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "Permission Missing",
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      "To mask screen defects, this app needs to draw over other apps.",
                    ),
                    const SizedBox(height: 12),
                    ElevatedButton(
                      onPressed: _requestPermission,
                      style: ElevatedButton.styleFrom(backgroundColor: Colors.redAccent),
                      child: const Text("Grant Permission"),
                    )
                  ],
                ),
              ),

            const SizedBox(height: 30),

            // 2. Main Controls
            Center(
              child: Column(
                children: [
                  // Power Button
                  GestureDetector(
                    onTap: _toggleOverlay,
                    child: Container(
                      width: 100,
                      height: 100,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: _isOverlayActive ? Colors.tealAccent.withOpacity(0.2) : Colors.grey.withOpacity(0.1),
                        border: Border.all(
                          color: _isOverlayActive ? Colors.tealAccent : Colors.grey,
                          width: 3,
                        ),
                        boxShadow: _isOverlayActive
                            ? [BoxShadow(color: Colors.tealAccent.withOpacity(0.4), blurRadius: 20)]
                            : [],
                      ),
                      child: Icon(
                        Icons.power_settings_new,
                        size: 40,
                        color: _isOverlayActive ? Colors.tealAccent : Colors.grey,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    _isOverlayActive ? "Active" : "Inactive",
                    style: TextStyle(
                      color: _isOverlayActive ? Colors.tealAccent : Colors.grey,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1.5,
                    ),
                  ),
                ],
              ),
            ),

            const Spacer(),

            // 3. Line Configuration
            Text("Line Settings", style: TextStyle(color: Colors.grey[400])),
            const Divider(color: Colors.grey),

            Row(
              children: [
                const Icon(Icons.line_weight, color: Colors.white),
                const SizedBox(width: 12),
                const Text("Thickness"),
                Expanded(
                  child: Slider(
                    value: _lineWidth,
                    min: 1,
                    max: 50,
                    divisions: 50,
                    label: "${_lineWidth.toInt()}px",
                    onChanged: _updateWidth,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton.icon(
                onPressed: _isOverlayActive ? _addLine : null,
                icon: const Icon(Icons.add),
                label: const Text("Add Vertical Line"),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white12,
                  foregroundColor: Colors.white,
                ),
              ),
            ),

            const SizedBox(height: 20),

            // 4. Profiles
             Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                OutlinedButton.icon(
                  onPressed: _isOverlayActive ? _saveProfile : null,
                  icon: const Icon(Icons.save),
                  label: const Text("Save Profile"),
                   style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.tealAccent,
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: _loadProfile,
                  icon: const Icon(Icons.folder_open),
                  label: const Text("Load Profile"),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.tealAccent,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 20),
            const Text(
              "Note: Use the floating on-screen buttons to position the line precisely.",
              style: TextStyle(fontSize: 12, color: Colors.grey),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
