// lib/screens/profile/profile_screen.dart
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import '../../main.dart';
import '../service/auth_service.dart';
import '../service/api_service.dart';

class ProfileScreen extends StatefulWidget {
  @override
  _ProfileScreenState createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  bool _isLoading = true;
  String _profileImagePath = 'assets/myphoto.png';
  String _userName = '로딩 중...';
  String _userEmail = '로딩 중...';
  int? _userId;
  Map<String, dynamic>? _childData;

  @override
  void initState() {
    super.initState();
    _loadUserData();
  }

  // ⭐ 실제 DB에서 사용자 데이터 불러오기
  Future<void> _loadUserData() async {
    try {
      setState(() {
        _isLoading = true;
      });

      // 1. AuthService에서 기본 정보 가져오기
      final accessToken = await AuthService.getAccessToken();
      final userId = await AuthService.getUserId();
      final userEmail = await AuthService.getUserEmail();

      if (accessToken == null || userId == null) {
        print('❌ [ProfileScreen] 로그인 정보 없음');
        Navigator.pushReplacementNamed(context, '/login');
        return;
      }

      setState(() {
        _userId = userId;
        _userEmail = userEmail ?? 'Unknown';
      });

      print('🔍 [ProfileScreen] 사용자 정보 로드: userId=$userId, email=$userEmail');

      // 2. 서버에서 상세 사용자 정보 가져오기 (선택사항)
      await _fetchUserProfileFromServer(accessToken, userId);

      // 3. 아이 정보도 함께 로드
      await _loadChildInfo();

    } catch (e) {
      print('❌ [ProfileScreen] 사용자 데이터 로드 오류: $e');
      // 기본값 설정
      setState(() {
        _userName = '사용자';
        _userEmail = _userEmail ?? 'Unknown';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  // ⭐ 서버에서 사용자 프로필 정보 가져오기
  Future<void> _fetchUserProfileFromServer(String accessToken, int userId) async {
    try {
      final dio = ApiService.dio;

      // 사용자 프로필 API 호출 (만약 있다면)
      // final response = await dio.get(
      //   '/api/user/profile',
      //   options: Options(headers: {'Authorization': 'Bearer $accessToken'}),
      // );

      // if (response.statusCode == 200) {
      //   final userData = response.data;
      //   setState(() {
      //     _userName = userData['name'] ?? userData['nickname'] ?? '사용자';
      //     _userEmail = userData['email'] ?? _userEmail;
      //     _profileImagePath = userData['profileImage'] ?? 'assets/myphoto.png';
      //   });
      //   return;
      // }

      // 현재는 DB 스키마에 맞게 기본값 설정
      // username이 "kakao_4287771333" 형태라면 nickname으로 표시
      if (_userEmail.isNotEmpty && _userEmail != 'Unknown') {
        final emailParts = _userEmail.split('@');
        setState(() {
          _userName = emailParts.isNotEmpty ? emailParts[0] : '사용자';
        });
      } else {
        setState(() {
          _userName = '사용자 #$userId';
        });
      }

    } catch (e) {
      print('❌ [ProfileScreen] 서버 프로필 조회 오류: $e');
      setState(() {
        _userName = '사용자 #$userId';
      });
    }
  }

  // ⭐ 아이 정보 로드
  Future<void> _loadChildInfo() async {
    try {
      final childInfo = await AuthService.checkChildInfo();
      if (childInfo != null && childInfo['hasChild'] == true) {
        setState(() {
          _childData = childInfo['childData'];
        });
        print('✅ [ProfileScreen] 아이 정보 로드: ${_childData?['name']}');
      }
    } catch (e) {
      print('❌ [ProfileScreen] 아이 정보 로드 오류: $e');
    }
  }

  // ⭐ AuthService를 사용한 로그아웃 함수
  Future<void> _logout() async {
    try {
      // 로그아웃 확인 다이얼로그
      final shouldLogout = await showDialog<bool>(
        context: context,
        builder: (BuildContext context) {
          return AlertDialog(
            title: Text('로그아웃'),
            content: Text('정말 로그아웃 하시겠습니까?'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: Text('취소'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                child: Text('로그아웃'),
                style: TextButton.styleFrom(foregroundColor: Colors.red),
              ),
            ],
          );
        },
      );

      if (shouldLogout != true) return;

      // 로딩 표시
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => Center(
          child: CircularProgressIndicator(
            valueColor: AlwaysStoppedAnimation<Color>(Color(0xFF8E97FD)),
          ),
        ),
      );

      // 1. 서버에 로그아웃 요청 (선택사항)
      final accessToken = await AuthService.getAccessToken();
      if (accessToken != null) {
        try {
          final dio = ApiService.dio;
          await dio.post(
            '/oauth/logout',
            options: Options(headers: {'Authorization': 'Bearer $accessToken'}),
          );
          print('✅ [ProfileScreen] 서버 로그아웃 성공');
        } catch (e) {
          print('⚠️ [ProfileScreen] 서버 로그아웃 실패 (계속 진행): $e');
        }
      }

      // 2. 로컬 토큰 삭제
      await AuthService.logout();

      // 로딩 다이얼로그 닫기
      Navigator.pop(context);

      // 3. 로그인 화면으로 이동
      Navigator.pushNamedAndRemoveUntil(
        context,
        '/login',
            (route) => false,
      );

    } catch (e) {
      print('❌ [ProfileScreen] 로그아웃 오류: $e');

      // 로딩 다이얼로그가 열려있다면 닫기
      Navigator.of(context, rootNavigator: true).pop();

      // 오류가 발생해도 로컬 토큰은 삭제하고 로그인 화면으로 이동
      await AuthService.logout();
      Navigator.pushNamedAndRemoveUntil(
        context,
        '/login',
            (route) => false,
      );
    }
  }

  // ⭐ 새로고침 기능
  Future<void> _refreshData() async {
    await _loadUserData();
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;

    if (_isLoading) {
      return BaseScaffold(
        child: SafeArea(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                CircularProgressIndicator(
                  valueColor: AlwaysStoppedAnimation<Color>(Color(0xFF8E97FD)),
                ),
                SizedBox(height: 16),
                Text(
                  '프로필 정보를 불러오는 중...',
                  style: TextStyle(
                    fontSize: 16,
                    color: Colors.black54,
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return BaseScaffold(
      child: SafeArea(
        child: RefreshIndicator(
          onRefresh: _refreshData,
          color: Color(0xFF8E97FD),
          child: SingleChildScrollView(
            physics: AlwaysScrollableScrollPhysics(),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: screenHeight - MediaQuery.of(context).padding.top - MediaQuery.of(context).padding.bottom,
              ),
              child: Column(
                children: [
                  // 상단 앱바
                  Container(
                    padding: EdgeInsets.symmetric(
                        horizontal: screenWidth * 0.05,
                        vertical: screenHeight * 0.02
                    ),
                    child: Row(
                      children: [
                        GestureDetector(
                          onTap: () => Navigator.pop(context),
                          child: Icon(
                            Icons.arrow_back,
                            color: Colors.black54,
                            size: screenWidth * 0.06,
                          ),
                        ),
                        Expanded(
                          child: Text(
                            'Profile',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: screenWidth * 0.045,
                              fontWeight: FontWeight.w600,
                              color: Colors.black87,
                            ),
                          ),
                        ),
                        GestureDetector(
                          onTap: _refreshData,
                          child: Icon(
                            Icons.refresh,
                            color: Colors.black54,
                            size: screenWidth * 0.06,
                          ),
                        ),
                      ],
                    ),
                  ),

                  SizedBox(height: screenHeight * 0.02),

                  // 프로필 이미지와 정보
                  Column(
                    children: [
                      // 프로필 이미지
                      Stack(
                        children: [
                          Container(
                            width: screenWidth * 0.3,
                            height: screenWidth * 0.3,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: Color(0xFFECA666),
                                width: 2.0,
                              ),
                            ),
                            child: ClipOval(
                              child: Container(
                                width: screenWidth * 0.3,
                                height: screenWidth * 0.3,
                                child: Image.asset(
                                  _profileImagePath,
                                  fit: BoxFit.cover,
                                  errorBuilder: (context, error, stackTrace) {
                                    return Container(
                                      color: Color(0xFFFDB5A6),
                                      child: Center(
                                        child: Text(
                                          '👤',
                                          style: TextStyle(fontSize: screenWidth * 0.1),
                                        ),
                                      ),
                                    );
                                  },
                                ),
                              ),
                            ),
                          ),
                          Positioned(
                            bottom: 0,
                            right: 0,
                            child: GestureDetector(
                              onTap: () {
                                _showImagePickerDialog(context);
                              },
                              child: Container(
                                width: screenWidth * 0.09,
                                height: screenWidth * 0.09,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: Color(0xFF8B5A6B),
                                ),
                                child: Icon(
                                  Icons.edit,
                                  color: Colors.white,
                                  size: screenWidth * 0.045,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),

                      SizedBox(height: screenHeight * 0.025),

                      // 사용자 정보
                      Column(
                        children: [
                          // ⭐ 아이 이름 우선 표시, 없으면 사용자 이름
                          Text(
                            _childData != null ? _childData!['name'] : _userName,
                            style: TextStyle(
                              fontSize: screenWidth * 0.06,
                              fontWeight: FontWeight.bold,
                              color: Colors.black87,
                            ),
                          ),

                          SizedBox(height: screenHeight * 0.01),

                          // ⭐ 아이가 있으면 부모님 표시, 없으면 이메일 표시
                          if (_childData != null) ...[
                            Text(
                              '${_childData!['name']}의 부모님',
                              style: TextStyle(
                                fontSize: screenWidth * 0.035,
                                color: Colors.black54,
                              ),
                            ),
                            SizedBox(height: screenHeight * 0.005),
                            Text(
                              _userEmail,
                              style: TextStyle(
                                fontSize: screenWidth * 0.032,
                                color: Colors.black38,
                              ),
                            ),
                          ] else ...[
                            Text(
                              _userEmail,
                              style: TextStyle(
                                fontSize: screenWidth * 0.04,
                                color: Colors.black54,
                              ),
                            ),
                            SizedBox(height: screenHeight * 0.01),
                            Container(
                              padding: EdgeInsets.symmetric(
                                horizontal: screenWidth * 0.04,
                                vertical: screenHeight * 0.008,
                              ),
                              decoration: BoxDecoration(
                                color: Colors.orange.withOpacity(0.1),
                                borderRadius: BorderRadius.circular(16),
                              ),
                              child: Text(
                                '아이 정보를 등록해주세요',
                                style: TextStyle(
                                  fontSize: screenWidth * 0.032,
                                  color: Colors.orange.shade700,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),

                  SizedBox(height: screenHeight * 0.04),

                  // 메뉴 리스트
                  Container(
                    padding: EdgeInsets.symmetric(horizontal: screenWidth * 0.05),
                    child: Column(
                      children: [
                        _buildMenuItem(
                          context,
                          icon: Icons.person,
                          title: _childData != null ? '아이 정보 수정' : '아이 정보 등록',
                          subtitle: _childData != null
                              ? '${_childData!['name']} 정보 수정'
                              : '아이 정보를 등록해주세요',
                          onTap: () async {
                            final result = await Navigator.pushNamed(context, '/profile-details');
                            if (result == true) {
                              _refreshData();
                            }
                          },
                        ),

                        SizedBox(height: screenHeight * 0.015),

                        _buildMenuItem(
                          context,
                          icon: Icons.settings,
                          title: 'Settings',
                          onTap: () {
                            Navigator.pushNamed(context, '/settings');
                          },
                        ),

                        SizedBox(height: screenHeight * 0.015),

                        _buildMenuItem(
                          context,
                          icon: Icons.contact_support,
                          title: 'Contacts',
                          onTap: () {
                            Navigator.pushNamed(context, '/contacts');
                          },
                        ),

                        SizedBox(height: screenHeight * 0.015),

                        _buildMenuItem(
                          context,
                          icon: Icons.help_outline,
                          title: 'Support',
                          onTap: () {
                            Navigator.pushNamed(context, '/support');
                          },
                        ),

                        SizedBox(height: screenHeight * 0.015),

                        _buildMenuItem(
                          context,
                          icon: Icons.logout,
                          title: 'Logout',
                          onTap: _logout,
                          isDestructive: true,
                        ),
                      ],
                    ),
                  ),

                  SizedBox(height: screenHeight * 0.03),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildMenuItem(
      BuildContext context, {
        required IconData icon,
        required String title,
        String? subtitle,
        required VoidCallback onTap,
        bool isDestructive = false,
      }) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: EdgeInsets.symmetric(
            horizontal: screenWidth * 0.05,
            vertical: screenHeight * 0.02
        ),
        decoration: BoxDecoration(
          color: isDestructive
              ? Colors.red.withOpacity(0.1)
              : Color(0xFFF5E6A3),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Container(
              width: screenWidth * 0.1,
              height: screenWidth * 0.1,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withOpacity(0.7),
              ),
              child: Icon(
                icon,
                color: isDestructive ? Colors.red : Color(0xFF8B5A6B),
                size: screenWidth * 0.05,
              ),
            ),

            SizedBox(width: screenWidth * 0.04),

            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: screenWidth * 0.04,
                      fontWeight: FontWeight.w500,
                      color: isDestructive ? Colors.red : Colors.black87,
                    ),
                  ),
                  if (subtitle != null) ...[
                    SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: screenWidth * 0.032,
                        color: isDestructive ? Colors.red.shade300 : Colors.black54,
                      ),
                    ),
                  ],
                ],
              ),
            ),

            Icon(
              Icons.arrow_forward_ios,
              color: isDestructive ? Colors.red : Colors.black38,
              size: screenWidth * 0.04,
            ),
          ],
        ),
      ),
    );
  }

  void _showImagePickerDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text('프로필 사진 변경'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: Icon(Icons.camera_alt),
                title: Text('카메라로 촬영'),
                onTap: () {
                  Navigator.pop(context);
                  _pickImageFromCamera();
                },
              ),
              ListTile(
                leading: Icon(Icons.photo_library),
                title: Text('갤러리에서 선택'),
                onTap: () {
                  Navigator.pop(context);
                  _pickImageFromGallery();
                },
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text('취소'),
            ),
          ],
        );
      },
    );
  }

  void _pickImageFromCamera() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('카메라 기능을 준비 중입니다.'),
        backgroundColor: Color(0xFF8E97FD),
      ),
    );
  }

  void _pickImageFromGallery() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('갤러리 기능을 준비 중입니다.'),
        backgroundColor: Color(0xFF8E97FD),
      ),
    );
  }
}