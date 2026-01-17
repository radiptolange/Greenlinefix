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

class LineData {
  String id;
  int x;
  int width;
  bool visible;

  LineData({
    required this.id,
    this.x = 0,
    this.width = 5,
    this.visible = true,
  });

  Map<String, dynamic> toJson() => {
    'id': id,
    'x': x,
    'width': width,
    'visible': visible,
  };

  factory LineData.fromJson(Map<String, dynamic> json) {
    return LineData(
      id: json['id'] ?? '',
      x: json['x'] ?? 0,
      width: json['width'] ?? 5,
      visible: json['visible'] ?? true,
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
  bool _hasPermission = false;

  List<LineData> _lines = [];
  String? _selectedLineId;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkPermission();
    _syncState();
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
    final bool hasPerm = await Permission.systemAlertWindow.isGranted;
    setState(() {
      _hasPermission = hasPerm;
    });
  }

  Future<void> _requestPermission() async {
    await Permission.systemAlertWindow.request();
  }

  Future<void> _syncState() async {
    // Try to get lines from native if service is running
    try {
      final result = await platform.invokeMethod('getLines');
      if (result != null) {
        final Map<dynamic, dynamic> data = result;
        final List<LineData> loadedLines = [];
        data.forEach((key, value) {
          final map = Map<String, dynamic>.from(value);
          loadedLines.add(LineData(
            id: key,
            x: map['x'] ?? 0,
            width: map['width'] ?? 5,
            visible: map['visible'] ?? true
          ));
        });

        setState(() {
          _lines = loadedLines;
          _isOverlayActive = true; // If we got lines, service is running
          if (_lines.isNotEmpty && _selectedLineId == null) {
             _selectedLineId = _lines.last.id;
          }
        });
      }
    } catch (e) {
      // Service probably not running
      setState(() {
        _isOverlayActive = false;
      });
    }
  }

  Future<void> _toggleOverlay() async {
    if (!_hasPermission) {
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
        setState(() {
          _isOverlayActive = false;
          _lines.clear();
          _selectedLineId = null;
        });
      } else {
        await platform.invokeMethod('startOverlay');
        setState(() {
          _isOverlayActive = true;
        });
        // Restore active profile? Or just empty?
        // Let's keep it empty or sync.
      }
    } on PlatformException catch (e) {
      debugPrint("Error: ${e.message}");
      if (e.code == "PERM_DENIED") {
         _checkPermission();
      }
    }
  }

  Future<void> _addLine() async {
    if (!_isOverlayActive) return;

    final id = DateTime.now().millisecondsSinceEpoch.toString();
    final newLine = LineData(id: id, width: 5);

    setState(() {
      _lines.add(newLine);
      _selectedLineId = id;
    });

    await platform.invokeMethod('addLine', {
      'id': newLine.id,
      'x': newLine.x,
      'width': newLine.width,
      'visible': newLine.visible
    });
  }

  Future<void> _removeLine(String id) async {
    setState(() {
      _lines.removeWhere((l) => l.id == id);
      if (_selectedLineId == id) {
        _selectedLineId = _lines.isNotEmpty ? _lines.last.id : null;
      }
    });
    await platform.invokeMethod('removeLine', {'id': id});
  }

  Future<void> _toggleLineVisibility(String id, bool visible) async {
    final index = _lines.indexWhere((l) => l.id == id);
    if (index != -1) {
      setState(() {
        _lines[index].visible = visible;
      });
      await platform.invokeMethod('toggleLine', {'id': id, 'visible': visible});
    }
  }

  Future<void> _selectLine(String id) async {
    setState(() {
      _selectedLineId = id;
    });
    await platform.invokeMethod('selectLine', {'id': id});
  }

  Future<void> _updateWidth(double val) async {
    if (_selectedLineId == null) return;

    final index = _lines.indexWhere((l) => l.id == _selectedLineId);
    if (index != -1) {
       setState(() {
         _lines[index].width = val.toInt();
       });
       await platform.invokeMethod('updateWidth', {
         'id': _selectedLineId,
         'width': val.toInt()
       });
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

        final linesJson = _lines.map((l) => l.toJson()).toList();

        profiles[name] = linesJson;
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
         await _toggleOverlay();
         if (!_isOverlayActive) return;
      }

      try {
        List<dynamic> linesRaw = profiles[selectedProfile];
        // Convert to map for native
        Map<String, Map<String, dynamic>> nativeMap = {};
        List<LineData> newLines = [];

        for (var item in linesRaw) {
          final line = LineData.fromJson(item);
          newLines.add(line);
          nativeMap[line.id] = {
            'x': line.x,
            'width': line.width,
            'visible': line.visible
          };
        }

        await platform.invokeMethod('setLines', {'lines': nativeMap});

        setState(() {
          _lines = newLines;
          if (_lines.isNotEmpty) {
            _selectedLineId = _lines.last.id;
          }
        });

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
    final selectedLine = _lines.firstWhere((l) => l.id == _selectedLineId, orElse: () => LineData(id: ''));

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
            // Permission UI
            if (!_hasPermission)
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.red.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.redAccent),
                ),
                child: Column(
                  children: [
                    const Text("Permission Missing", style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    ElevatedButton(
                      onPressed: _requestPermission,
                      style: ElevatedButton.styleFrom(backgroundColor: Colors.redAccent),
                      child: const Text("Grant Permission"),
                    )
                  ],
                ),
              ),

            const SizedBox(height: 20),

            // Power Button
            Center(
              child: GestureDetector(
                onTap: _toggleOverlay,
                child: Container(
                  width: 80,
                  height: 80,
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
                    size: 32,
                    color: _isOverlayActive ? Colors.tealAccent : Colors.grey,
                  ),
                ),
              ),
            ),

            const SizedBox(height: 20),

            // Lines List
            Expanded(
              child: _lines.isEmpty
                ? Center(child: Text("No active lines", style: TextStyle(color: Colors.grey[600])))
                : ListView.builder(
                    itemCount: _lines.length,
                    itemBuilder: (context, index) {
                      final line = _lines[index];
                      final isSelected = line.id == _selectedLineId;

                      return Container(
                        margin: const EdgeInsets.symmetric(vertical: 4),
                        decoration: BoxDecoration(
                          color: isSelected ? Colors.tealAccent.withOpacity(0.1) : Colors.white10,
                          border: isSelected ? Border.all(color: Colors.tealAccent) : null,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: ListTile(
                          onTap: () => _selectLine(line.id),
                          title: Text("Line ${index + 1}"),
                          subtitle: Text("Width: ${line.width}px"),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Switch(
                                value: line.visible,
                                onChanged: (v) => _toggleLineVisibility(line.id, v),
                                activeColor: Colors.tealAccent,
                              ),
                              IconButton(
                                icon: const Icon(Icons.delete, color: Colors.redAccent),
                                onPressed: () => _removeLine(line.id),
                              )
                            ],
                          ),
                        ),
                      );
                    },
                  ),
            ),

            const SizedBox(height: 10),

            // Controls for Selected Line
            if (_selectedLineId != null && _lines.any((l) => l.id == _selectedLineId))
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text("Selected Line Width", style: TextStyle(color: Colors.grey[400])),
                   Row(
                    children: [
                      const Icon(Icons.line_weight, color: Colors.white),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Slider(
                          value: selectedLine.width.toDouble(),
                          min: 1,
                          max: 50,
                          divisions: 50,
                          label: "${selectedLine.width}px",
                          onChanged: _updateWidth,
                        ),
                      ),
                    ],
                  ),
                ],
              ),

             SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: _isOverlayActive ? _addLine : null,
                icon: const Icon(Icons.add),
                label: const Text("Add New Line"),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white12,
                  foregroundColor: Colors.white,
                ),
              ),
            ),

            const SizedBox(height: 20),

            // Profiles
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
          ],
        ),
      ),
    );
  }
}
