package com.securetalk.controller;

import com.securetalk.model.Role;
import com.securetalk.model.User;
import com.securetalk.model.UserStatus;
import com.securetalk.payload.response.MessageResponse;
import com.securetalk.security.services.UserDetailsImpl;
import com.securetalk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        // Récupérer l'utilisateur connecté
        UserDetailsImpl currentUser = getCurrentUser();
        
        // Récupérer tous les utilisateurs sauf l'utilisateur connecté
        List<User> users = userService.getAllUsers().stream()
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .collect(Collectors.toList());
        
        // Convertir en DTO pour ne pas exposer les données sensibles
        List<UserDto> userDtos = users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(userDtos);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(convertToDto(user)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> updateStatus(@RequestBody StatusUpdateRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        
        User updatedUser = userService.getUserById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        updatedUser.setStatus(request.getStatus());
        userService.updateUserStatus(currentUser.getId(), request.isOnline());
        
        return ResponseEntity.ok(new MessageResponse("Statut mis à jour avec succès"));
    }
    
    @PutMapping("/password")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> updatePassword(@RequestBody PasswordUpdateRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        
        try {
            userService.updatePassword(currentUser.getId(), request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok(new MessageResponse("Mot de passe mis à jour avec succès"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
    
    private UserDetailsImpl getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UserDetailsImpl) authentication.getPrincipal();
    }
    
    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setOnline(user.isOnline());
        dto.setLastSeen(user.getLastSeen());
        dto.setStatus(user.getStatus());
        
        // Convertir les rôles
        List<RoleDto> roleDtos = new ArrayList<>();
        if (user.getRoles() != null) {
            roleDtos = user.getRoles().stream()
                    .map(role -> {
                        RoleDto roleDto = new RoleDto();
                        roleDto.setId(role.getId());
                        roleDto.setName(role.getName().name());
                        return roleDto;
                    })
                    .collect(Collectors.toList());
        }
        dto.setRoles(roleDtos);
        
        // Ajouter les autres champs
        dto.setEnabled(true); // Par défaut, tous les utilisateurs sont activés
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        
        // Formater la date de création
        if (user.getCreatedAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            dto.setCreatedAt(user.getCreatedAt().format(formatter));
        }
        
        return dto;
    }
    
    // Classes internes pour les DTO
    
    public static class UserDto {
        private Long id;
        private String username;
        private String email;
        private boolean online;
        private java.time.LocalDateTime lastSeen;
        private UserStatus status;
        private List<RoleDto> roles;
        private boolean enabled = true;
        private String firstName;
        private String lastName;
        private boolean isPrivate = false;
        private boolean allowMessages = true;
        private boolean showOnlineStatus = true;
        private String createdAt;
        
        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public boolean isOnline() { return online; }
        public void setOnline(boolean online) { this.online = online; }
        
        public java.time.LocalDateTime getLastSeen() { return lastSeen; }
        public void setLastSeen(java.time.LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
        
        public UserStatus getStatus() { return status; }
        public void setStatus(UserStatus status) { this.status = status; }
        
        public List<RoleDto> getRoles() { return roles; }
        public void setRoles(List<RoleDto> roles) { this.roles = roles; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        
        public boolean isPrivate() { return isPrivate; }
        public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
        
        public boolean isAllowMessages() { return allowMessages; }
        public void setAllowMessages(boolean allowMessages) { this.allowMessages = allowMessages; }
        
        public boolean isShowOnlineStatus() { return showOnlineStatus; }
        public void setShowOnlineStatus(boolean showOnlineStatus) { this.showOnlineStatus = showOnlineStatus; }
        
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
    
    public static class StatusUpdateRequest {
        private boolean online;
        private UserStatus status;
        
        public boolean isOnline() { return online; }
        public void setOnline(boolean online) { this.online = online; }
        
        public UserStatus getStatus() { return status; }
        public void setStatus(UserStatus status) { this.status = status; }
    }
    
    public static class PasswordUpdateRequest {
        private String currentPassword;
        private String newPassword;
        
        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
    
    public static class RoleDto {
        private Integer id;
        private String name;
        
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
