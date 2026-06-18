package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.ApiResponse;
import com.tlcn.sportsnet_backend.dto.account.AccountRegisterRequest;
import com.tlcn.sportsnet_backend.dto.account.AccountResponse;
import com.tlcn.sportsnet_backend.dto.account.AccountUpdatePassword;
import com.tlcn.sportsnet_backend.dto.auth.LoginDTO;
import com.tlcn.sportsnet_backend.dto.auth.VerifyRequest;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.error.UnauthorizedException;
import com.tlcn.sportsnet_backend.repository.AccountRepository;
import com.tlcn.sportsnet_backend.repository.RoleRepository;
import com.tlcn.sportsnet_backend.service.AccountService;
import com.tlcn.sportsnet_backend.service.MailService;
import com.tlcn.sportsnet_backend.service.OTPService;
import com.tlcn.sportsnet_backend.service.RefreshTokenService;
import com.tlcn.sportsnet_backend.util.SecurityUtil;
import com.tlcn.sportsnet_backend.util.SlugUtil;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SecurityUtil securityUtil;
    private final AuthenticationManager authenticationManager;
    private final AccountService accountService;
    private final RefreshTokenService refreshTokenService;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    @Value("${jwt.token-verify-validity-in-seconds}")
    private long refreshTokenExpiration;
    @Value("${jwt.token-create-validity-in-seconds}")
    private long expire_access;
    private final OTPService otpService;
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginDTO loginDTO,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request
    ) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginDTO.getEmail(), loginDTO.getPassword());
        Account account = accountService.findByEmail(loginDTO.getEmail())
                        .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        try {

            // Xác thực đăng nhập
            Authentication authentication = authenticationManager.authenticate(authenticationToken);

            // Tạo access token
            String accessToken = securityUtil.createToken(authentication);

            if (deviceId == null || deviceId.isBlank()) {
                deviceId = UUID.randomUUID().toString();
            }

            String refreshToken = refreshTokenService.create(
                    account,
                    deviceId,
                    request.getHeader("User-Agent"),
                    request.getRemoteAddr()
            );

            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(refreshTokenExpiration)
                    .build();

            ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                    .httpOnly(true) // nếu chỉ muốn server-side đọc
                    .secure(true)
                    .path("/")
                    .maxAge(expire_access)
                    .build();
            ResponseCookie deviceIdCookie = ResponseCookie.from("deviceId", deviceId)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .build();


            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, deviceIdCookie.toString())
                    .body(ApiResponse.success(Map.of("accessToken", accessToken, "refreshToken", refreshToken, "deviceId", deviceId)));

        } catch (LockedException e) {
            throw new InvalidDataException("Tài khoản bị khóa");
        } catch (DisabledException e) {
            otpService.createOTP(account);
            throw new InvalidDataException("Tài khoản chưa được kích hoạt");
        } catch (BadCredentialsException e) {
            throw new InvalidDataException("Email hoặc mật khẩu không đúng");
        }
    }

    @PostMapping("/login/firebase")
    public ResponseEntity<?> loginWithFirebase(@RequestBody Map<String, String> body,
                                               @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                               HttpServletRequest request) {
        try {
            String idToken = body.get("idToken");
            if (idToken == null || idToken.isBlank()) {
                throw new InvalidDataException("Thiếu idToken từ Firebase");
            }

            // Xác thực token Firebase
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String email = decodedToken.getEmail();
            String name = decodedToken.getName();

            // Kiểm tra account đã tồn tại chưa
            Optional<Account> optionalAccount = accountRepository.findByEmail(email);

            Account account;
            if (optionalAccount.isPresent()) {
                account = optionalAccount.get();
            } else {
                // Tạo mới account nếu chưa tồn tại
                Role role = roleRepository.findByName("ROLE_USER")
                        .orElseThrow(() -> new RuntimeException("Role USER không tồn tại"));

                account = Account.builder()
                        .email(email)
                        .enabled(true)
                        .verified(true)
                        .roles(Set.of(role))
                        .reputationScore(100)
                        .password(UUID.randomUUID().toString()) // tránh null
                        .build();

                UserInfo userInfo = UserInfo.builder()
                        .fullName(name != null ? name : email)

                        .account(account)
                        .build();

                account.setUserInfo(userInfo);
                account = accountRepository.save(account);
            }

            // Sinh access token nội bộ
            String accessToken = securityUtil.createTokenFromAccount(account);

            // Sinh refresh token
            if (deviceId == null || deviceId.isBlank()) {
                deviceId = UUID.randomUUID().toString();
            }

            String refreshToken = refreshTokenService.create(
                    account,
                    deviceId,
                    request.getHeader("User-Agent"),
                    request.getRemoteAddr()
            );

            // Tạo cookies
            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(refreshTokenExpiration)
                    .build();

            ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(expire_access)
                    .build();

            ResponseCookie deviceIdCookie = ResponseCookie.from("deviceId", deviceId)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, deviceIdCookie.toString())
                    .body(ApiResponse.success(Map.of(
                            "accessToken", accessToken,
                            "refreshToken", refreshToken,
                            "deviceId", deviceId,
                            "email", email
                    )));

        } catch (Exception e) {
            e.printStackTrace();
            throw new UnauthorizedException("Đăng nhập bằng Google thất bại: " + e.getMessage());
        }
    }

    @PostMapping("/forget/{email}")
    public ResponseEntity<?> forget(@PathVariable String email){
        Account account = accountRepository.findByEmail(email).orElseThrow(()-> new UnauthorizedException("Account không tồn tại"));
        String newPassword = SlugUtil.randomString(8);
        account.setPassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
        mailService.sendResetPasswordEmail(email,newPassword);
        return ResponseEntity.ok("Reset password thành công");
    }

    @GetMapping("/send-otp/{email}")
    public ResponseEntity<?> sendOtp(@PathVariable String email) {
        Account account = accountRepository.findByEmail(email).orElseThrow(() -> new UnauthorizedException("Account không tồn tại"));
        otpService.createOTP(account);
        return ResponseEntity.ok("Gửi OTP thành công");
    }

    @GetMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(
            @RequestHeader( "refreshToken") String refreshToken,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (refreshToken.isBlank() || refreshToken.isEmpty()) {
            throw new UnauthorizedException("Bạn chưa đăng nhập hoặc thiếu refresh token");
        }

        RefreshToken token = refreshTokenService.verify(refreshToken, deviceId);
        Account account = token.getAccount();

        String newAccessToken = securityUtil.createTokenFromAccount(account);
        String newRefresh = refreshTokenService.create(
                account,
                deviceId,
                token.getUserAgent(),
                token.getIpAddress()
        );

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", newRefresh)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .build();

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", newAccessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(expire_access)
                .build();

        ResponseCookie deviceIdCookie = ResponseCookie.from("deviceId", deviceId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, deviceIdCookie.toString())
                .body(ApiResponse.success(Map.of("accessToken", newAccessToken, "refreshToken" , newRefresh, "deviceId" , deviceId)));
    }


    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader("refreshToken") String refreshToken,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        refreshTokenService.revoke(refreshToken, deviceId); // tìm theo token + deviceId và xóa

        ResponseCookie clearRefreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie clearAccessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString())
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie.toString())
                .body(ApiResponse.success("Đăng xuất thành công", null));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyToken(@RequestBody VerifyRequest request,HttpServletRequest headerRequest, @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
      Account account = otpService.verifyOTP(request);
       return ResponseEntity.ok("Xác thực tài khoản thành công");

    }

    @Transactional
    @PostMapping("/register")
    public ResponseEntity<?> registerAccount(@RequestBody AccountRegisterRequest registerRequest
                                             ) {
        Account account = accountService.registerAccount(registerRequest);
                otpService.createOTP(account);
       return ResponseEntity.ok("Đăng ký thành công");
    }

    @PutMapping("/update-password")
    public ResponseEntity<?> updatePass(@RequestBody AccountUpdatePassword updatePassword) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        boolean match = passwordEncoder.matches(updatePassword.getPassword(), account.getPassword());
        if(!match){
            throw new InvalidDataException("Mật khẩu hiện tại không đúng");
        }
        if(!updatePassword.getNewPassword().equals(updatePassword.getConfirmPassword())) {
            throw new InvalidDataException("Mật khẩu xác thực không khớp");
        }
        account.setPassword(passwordEncoder.encode(updatePassword.getNewPassword()));
        accountRepository.save(account);
        return ResponseEntity.ok("Thay đổi mật khẩu thành công");

    }


}
