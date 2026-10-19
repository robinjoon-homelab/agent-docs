package xyz.robinjoon.agentdocs.access.application.port.input

class SignInRefusedException(
    login: String,
) : RuntimeException("GitHub account $login is not allowed to sign in")
