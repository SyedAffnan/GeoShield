import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/network/conflict_exception.dart';
import '../../../core/network/network_exception.dart';
import '../../../core/network/validation_exception.dart';

/// Creates a tourist account through `POST /api/v1/auth/register`. The client-side
/// rules below mirror the server's `RegisterRequest` constraints exactly; the server
/// remains authoritative and its rejection messages are shown verbatim.
class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  /// `RegisterRequest.username` — `^[A-Za-z0-9]{3,30}$`.
  static final _usernamePattern = RegExp(r'^[A-Za-z0-9]{3,30}$');

  /// `RegisterRequest.password` — the backend `PasswordValidator` pattern.
  static final _passwordPattern =
      RegExp(r'^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$');

  /// `RegisterRequest.phoneNumber` — E.164, as enforced by `PhoneNumberValidator`.
  static final _phonePattern = RegExp(r'^\+[1-9]\d{1,14}$');

  static final _emailPattern = RegExp(r'^[^@\s]+@[^@\s]+$');

  final _formKey = GlobalKey<FormState>();
  final _fullNameController = TextEditingController();
  final _usernameController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  bool _obscurePassword = true;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _fullNameController.dispose();
    _usernameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _register() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final account = await ref.read(authRepositoryProvider).register(
            username: _usernameController.text.trim(),
            email: _emailController.text.trim(),
            password: _passwordController.text,
            fullName: _fullNameController.text.trim(),
            phoneNumber: _phoneController.text.trim(),
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(
            'Account created for ${account.email} as ${account.role}. Please sign in.'),
      ));
      context.go('/login');
    } on ConflictException catch (error) {
      _showError(error.message);
    } on ValidationException catch (error) {
      _showError(error.message);
    } on NetworkException {
      _showError(
          'Unable to reach GeoShield. Check your connection or the configured backend URL.');
    } catch (_) {
      _showError('Unable to create the account. Please try again.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _showError(String message) {
    if (mounted) setState(() => _error = message);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Create account'),
        actions: [
          IconButton(
            tooltip: 'API configuration',
            onPressed: () => context.push('/settings'),
            icon: const Icon(Icons.settings_outlined),
          ),
        ],
      ),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text('Register as a tourist',
                          style: Theme.of(context).textTheme.headlineSmall),
                      const SizedBox(height: 8),
                      const Text(
                          'GeoShield creates every new account with the TOURIST role.'),
                      const SizedBox(height: 24),
                      TextFormField(
                        controller: _fullNameController,
                        textCapitalization: TextCapitalization.words,
                        decoration: const InputDecoration(
                            labelText: 'Full name',
                            border: OutlineInputBorder()),
                        validator: (value) {
                          final input = value?.trim() ?? '';
                          if (input.isEmpty) return 'Enter your full name.';
                          if (input.length > 255) {
                            return 'Full name must be 255 characters or fewer.';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _usernameController,
                        autocorrect: false,
                        decoration: const InputDecoration(
                          labelText: 'Username',
                          helperText: '3 to 30 letters and digits only',
                          border: OutlineInputBorder(),
                        ),
                        validator: (value) =>
                            _usernamePattern.hasMatch(value?.trim() ?? '')
                                ? null
                                : 'Use 3 to 30 letters or digits, with no spaces or symbols.',
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _emailController,
                        keyboardType: TextInputType.emailAddress,
                        autocorrect: false,
                        decoration: const InputDecoration(
                            labelText: 'Email', border: OutlineInputBorder()),
                        validator: (value) =>
                            _emailPattern.hasMatch(value?.trim() ?? '')
                                ? null
                                : 'Enter a valid email address.',
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _phoneController,
                        keyboardType: TextInputType.phone,
                        decoration: const InputDecoration(
                          labelText: 'Phone number',
                          helperText: 'International format, e.g. +919876543210',
                          border: OutlineInputBorder(),
                        ),
                        validator: (value) =>
                            _phonePattern.hasMatch(value?.trim() ?? '')
                                ? null
                                : 'Enter the number in international format, starting with +.',
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _passwordController,
                        obscureText: _obscurePassword,
                        decoration: InputDecoration(
                          labelText: 'Password',
                          helperText:
                              'At least 8 characters with upper case, lower case, a digit, and a symbol',
                          helperMaxLines: 2,
                          border: const OutlineInputBorder(),
                          suffixIcon: IconButton(
                            onPressed: () => setState(
                                () => _obscurePassword = !_obscurePassword),
                            icon: Icon(_obscurePassword
                                ? Icons.visibility
                                : Icons.visibility_off),
                          ),
                        ),
                        validator: (value) =>
                            _passwordPattern.hasMatch(value ?? '')
                                ? null
                                : 'Use at least 8 characters including an upper case letter, a lower case letter, a digit, and a special character.',
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _confirmPasswordController,
                        obscureText: _obscurePassword,
                        decoration: const InputDecoration(
                            labelText: 'Confirm password',
                            border: OutlineInputBorder()),
                        validator: (value) =>
                            value == _passwordController.text
                                ? null
                                : 'Both passwords must match.',
                        onFieldSubmitted: (_) => _register(),
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 16),
                        Text(_error!,
                            style: TextStyle(
                                color: Theme.of(context).colorScheme.error)),
                      ],
                      const SizedBox(height: 24),
                      FilledButton(
                        onPressed: _submitting ? null : _register,
                        child: _submitting
                            ? const SizedBox(
                                height: 20,
                                width: 20,
                                child:
                                    CircularProgressIndicator(strokeWidth: 2))
                            : const Text('Register'),
                      ),
                      const SizedBox(height: 8),
                      TextButton(
                        onPressed:
                            _submitting ? null : () => context.go('/login'),
                        child: const Text('Already registered? Sign in'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
